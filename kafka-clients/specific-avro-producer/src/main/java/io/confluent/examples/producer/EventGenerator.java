package io.confluent.examples.producer;

import JavaSessionize.avro.LogLine;

import java.util.Date;
import java.util.Random;

public class EventGenerator {

    static int numUsers = 10; // 10 users is easy to follow in a demo
    static int currUser;
    static String[] websites = {"support.html","about.html","foo.html", "bar.html", "home.html", "search.html", "list.html", "help.html", "bar.html", "foo.html"};


    public static LogLine getNext() {

        Random r = new Random();
        LogLine event = new LogLine();
        currUser = r.nextInt(numUsers);
        int ip4 = currUser;
        long runtime = new Date().getTime();

        event.setIp("66.249.1."+ ip4);
        event.setReferrer("www.example.com");
        event.setTimestamp(runtime);
        event.setUrl(websites[r.nextInt(websites.length)]);
        event.setUseragent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.125 Safari/537.36");

        return event;
    }
}
