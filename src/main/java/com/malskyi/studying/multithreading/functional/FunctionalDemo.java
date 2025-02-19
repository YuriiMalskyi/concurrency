package com.malskyi.studying.multithreading.functional;

import com.malskyi.studying.multithreading.Container;
import com.malskyi.studying.multithreading.ContainerStatus;
import lombok.Data;

import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Results:
 * 	Initialized: 18 containers
 * 	Built: 18 containers
 * 	Deployed: 18 containers
 */
public class FunctionalDemo {
    private static final int THREADS_COUNT = 3;
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    public static void main(String[] args) throws InterruptedException {
        final Queue<Container> initializedContainers = new ConcurrentLinkedQueue<>();
        final Queue<Container> builtContainers = new ConcurrentLinkedQueue<>();
        final Queue<Container> deployedContainers = new ConcurrentLinkedQueue<>();

        System.out.println("Starting parallel streams...");

        ForkJoinPool forkJoinPool = new ForkJoinPool(THREADS_COUNT);
        forkJoinPool.submit(() -> {
            Instant startTime = Instant.now();
            Instant endTime = startTime.plusSeconds(6);
            Stream.generate(() -> null)
                    .takeWhile(obj -> Instant.now().isBefore(endTime))
                    .parallel()
                    .map(container -> init(100L, initializedContainers))
                    .map(container -> build(container, 300L, builtContainers))
                    .forEach(container -> deploy(container, 600L, deployedContainers));
        }).join();

        Thread.sleep(300L);
        System.out.println("Results:");
        System.out.println("\tInitialized: " + initializedContainers.size() + " containers");
        System.out.println("\tBuilt: " + builtContainers.size() + " containers");
        System.out.println("\tDeployed: " + deployedContainers.size() + " containers");

        System.exit(0);
    }


    private static Container init(long delay, final Queue<Container> initializedContainers) {
        System.out.printf("[%s] Initializing container...%n", Thread.currentThread().getName());
        final Container container = new Container(COUNTER.getAndIncrement());
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            System.out.printf("[%s] InterruptedException during container initialization%n", Thread.currentThread().getName());
            System.out.printf("[%s] Stopping initialization process!%n", Thread.currentThread().getName());
            throw new RuntimeException(e);
        }
        container.setInitializedBy(String.format("[%s] %s ", Thread.currentThread().getName(), Thread.currentThread().getName()));
        container.setContainerStatus(ContainerStatus.INITIALIZED);
        initializedContainers.add(container);
        return container;
    }

    private static Container build(final Container container, long delay, final Queue<Container> builtContainers) {
        System.out.printf("[%s] Building %s...%n", Thread.currentThread().getName(), container.getName());
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            System.out.printf("[%s] InterruptedException caught during build%n", Thread.currentThread().getName());
            System.out.printf("[%s] Finishing process without completion%n", Thread.currentThread().getName());
            throw new RuntimeException(e);
        }
        container.setBuildBy(Thread.currentThread().getName());
        container.setContainerStatus(ContainerStatus.BUILT);
        builtContainers.add(container);
        return container;
    }

    private static void deploy(Container container, long delay, Queue<Container> deployedContainers) {
        System.out.printf("[%s] Deploying %s...%n", Thread.currentThread().getName(), container.getName());
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            System.out.printf("[%s] InterruptedException caught inside deploy worker during deployment finish!%n", Thread.currentThread().getName());
            System.out.printf("[%s] Currently deployed %s containers%n", Thread.currentThread().getName(), deployedContainers.size());
            System.out.printf("[%s] Finishing deployment process!%n", Thread.currentThread().getName());
            throw new RuntimeException(e);
        } finally {
            container.setDeployedBy(String.format("[%s] %s ", Thread.currentThread().getName(), Thread.currentThread().getName()));
            container.setContainerStatus(ContainerStatus.DEPLOYED);
            deployedContainers.add(container);
        }
    }
}
