package com.jasongj.kafka.stream;

import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

public class RankProcessor implements Processor<String, Integer> {

    @Override
    public void init(ProcessorContext context) {

    }

    @Override
    public void process(String key, Integer value) {

    }

    @Override
    public void punctuate(long timestamp) {

    }

    @Override
    public void close() {

    }
}
