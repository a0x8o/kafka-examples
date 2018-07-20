package io.confluent.examples.consumer;

import JavaSessionize.avro.LogLine;
import io.confluent.kafka.serializers.KafkaAvroDecoder;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;
import kafka.serializer.StringDecoder;
import kafka.utils.VerifiableProperties;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificData;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class AvroClicksSessionizer {
    private final ConsumerConnector consumer;
    private final KafkaProducer<String, LogLine> producer;
    private final String inputTopic;
    private final String outputTopic;
    private final String zookeeper;
    private final String groupId;
    private final String url;
    private final Map<String, SessionState> state = new HashMap<String, SessionState>();
    private final int sessionLengthMs;
    private static AvroClicksSessionizer sessionizer;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Please provide command line arguments: "
                    + "schemaRegistryUrl");
            System.exit(-1);
        }

        // currently hardcoding a lot of parameters, for simplicity
        String zookeeper = "localhost:2181";
        String groupId = "AvroClicksSessionizer";
        String inputTopic = "clicks";
        String outputTopic = "sessionized_clicks";
        String url = args[0];

        // Typically events are considered to be part of the same session if they are less than 30 minutes apart
        // To make this example show interesting results sooner, we limit the interval to 5 seconds
        int sessionLengthMs = 5*1000;


        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                sessionizer.consumer.shutdown();
            }
        });

        sessionizer = new AvroClicksSessionizer(zookeeper, groupId, inputTopic, outputTopic, url, sessionLengthMs);
        sessionizer.run();
    }

    public AvroClicksSessionizer(String zookeeper, String groupId, String inputTopic, String outputTopic, String url, int sessionLengthMs) {
        this.consumer = kafka.consumer.Consumer.createJavaConsumerConnector(
                new ConsumerConfig(createConsumerConfig(zookeeper, groupId, url)));
        this.producer = getProducer(url);
        this.zookeeper = zookeeper;
        this.groupId = groupId;
        this.inputTopic = inputTopic;
        this.outputTopic = outputTopic;
        this.url = url;
        this.sessionLengthMs = sessionLengthMs;
    }

    private Properties createConsumerConfig(String zookeeper, String groupId, String url) {
        Properties props = new Properties();
        props.put("zookeeper.connect", zookeeper);
        props.put("group.id", groupId);
        props.put("schema.registry.url", url);
        props.put("specific.avro.reader", true);

        // We configure the consumer to avoid committing offsets and to always start consuming from beginning of topic
        // This is not a best practice, but we want the example consumer to show results when running it again and again
        props.put("auto.commit.enable", "false");
        props.put("auto.offset.reset", "smallest");

        return props;
    }

    private void run() {
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();

        // Hard coding single threaded consumer
        topicCountMap.put(inputTopic, 1);

        Properties props = createConsumerConfig(zookeeper, groupId, url);
        VerifiableProperties vProps = new VerifiableProperties(props);

        // Create decoders for key and value
        KafkaAvroDecoder avroDecoder = new KafkaAvroDecoder(vProps);
        StringDecoder stringDecoder = new StringDecoder(new VerifiableProperties());

        KafkaStream stream = consumer.createMessageStreams(topicCountMap, stringDecoder, avroDecoder).get(inputTopic).get(0);
        ConsumerIterator it = stream.iterator();
        System.out.println("Ready to start iterating wih properties: " + props.toString());
        System.out.println("Reading topic:" + inputTopic);

        while (it.hasNext()) {
            MessageAndMetadata messageAndMetadata = it.next();
            String ip = (String) messageAndMetadata.key();

            // Once we release a new version of the avro deserializer that can return SpecificData, the deep copy will be unnecessary
            GenericRecord genericEvent = (GenericRecord) messageAndMetadata.message();
            LogLine event = (LogLine) SpecificData.get().deepCopy(LogLine.SCHEMA$, genericEvent);

            SessionState oldState = state.get(ip);
            int sessionId = 0;
            if (oldState == null) {
                state.put(ip, new SessionState(event.getTimestamp(), 0));
            } else {
                sessionId = oldState.getSessionId();

                // if the old timestamp is more than 30 minutes older than new one, we have a new session
                if (oldState.getLastConnection() < event.getTimestamp() - sessionLengthMs)
                    sessionId = sessionId + 1;

                SessionState newState = new SessionState(event.getTimestamp(), sessionId);
                state.put(ip, newState);
            }
            event.setSessionid(sessionId);
            System.out.println(event.toString());
            ProducerRecord<String, LogLine> record = new ProducerRecord<String, LogLine>(outputTopic, event.getIp().toString(), event);
            producer.send(record);
        }
    }

    private KafkaProducer<String, LogLine> getProducer(String url) {
        Properties props = new Properties();

        // hardcoding the Kafka server URI for this example
        props.put("bootstrap.servers", "localhost:9092");
        props.put("acks", "all");
        props.put("retries", 0);
        props.put("key.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
        props.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
        props.put("schema.registry.url", url);

        KafkaProducer<String, LogLine> producer = new KafkaProducer<String, LogLine>(props);
        return producer;
    }
}

