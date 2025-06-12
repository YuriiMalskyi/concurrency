package com.malskyi.studying.multithreading.producer_consumer;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ProducerConsumerDemo {
    private static final int SHARED_BUFFER_CAPACITY = 1;
    private static final int PRODUCERS_COUNT = 1;
    private static final int CONSUMERS_COUNT = 20;
    private static final long PRODUCER_DELAY = 300L;
    private static final long CONSUMER_DELAY = 600L;

    private static final long EXECUTION_TIME_SECONDS = 60L;

    public static void main(String[] args) throws InterruptedException {
        final AtomicInteger producerCallCount = new AtomicInteger(0);

        SharedBuffer sharedBuffer = new SharedBuffer(SHARED_BUFFER_CAPACITY);

        List<Thread> producers = Stream.generate(new ProducerSupplier(sharedBuffer, producerCallCount))
                .limit(PRODUCERS_COUNT)
                .toList();
        List<Thread> consumers = Stream.generate(new ConsumerSupplier(sharedBuffer))
                .limit(CONSUMERS_COUNT)
                .toList();

        producers.forEach(Thread::start);
        consumers.forEach(Thread::start);

        Instant deadline = Instant.now().plusSeconds(EXECUTION_TIME_SECONDS);
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(1000L);
        }

        producers.forEach(Thread::interrupt);
        consumers.forEach(Thread::interrupt);

        System.out.println(producerCallCount);
    }

    private static class SharedBuffer {
        private final Queue<String> queue = new LinkedList<>();
        private final int capacity;

        private SharedBuffer(int capacity) {
            this.capacity = capacity;
        }

        public synchronized void put(String message) {
            while (queue.size() >= capacity) {
                try {
//                    Thread.sleep(100L);
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            queue.add(message);
            notify();
//            notifyAll();
        }

        public synchronized String get() {
            while (queue.isEmpty()) {
                try {
//                    Thread.sleep(100L);
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            String polledData = queue.poll();
            notify();
//            notifyAll();
            return polledData;
        }
    }

    private record Producer(String producerName, SharedBuffer sharedBuffer,
                            AtomicInteger producerCallCount) implements Runnable {
        private static final AtomicInteger COUNTER = new AtomicInteger();

        @Override
        public void run() {
            while (true) {
                producerCallCount.incrementAndGet();
                String message = String.format("[%s]Message #%s", producerName, COUNTER.getAndIncrement());
                sharedBuffer.put(message);
                System.out.printf("[%s] Produced: %s%n", producerName, message);
                try {
                    Thread.sleep(PRODUCER_DELAY);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private record Consumer(String consumerName, SharedBuffer sharedBuffer) implements Runnable {
        @Override
        public void run() {
            while (true) {
                String message = sharedBuffer.get();
                try {
                    Thread.sleep(CONSUMER_DELAY);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.out.printf("[%s] Consumed: %s%n", consumerName, message);
            }
        }
    }

    private record ProducerSupplier(SharedBuffer sharedBuffer,
                                    AtomicInteger producerCallCount) implements Supplier<Thread> {
        private static int idCounter = 1; // Counter to generate unique IDs

        @Override
        public Thread get() {
            return new Thread(new Producer("Producer-" + idCounter++, sharedBuffer, producerCallCount));
        }
    }

    private record ConsumerSupplier(SharedBuffer sharedBuffer) implements Supplier<Thread> {
        private static int idCounter = 1; // Counter to generate unique IDs

        @Override
        public Thread get() {
            return new Thread(new Consumer("Consumer-" + idCounter++, sharedBuffer));
        }
    }
}
