package com.malskyi.studying.multithreading.parallel_workers;

import com.malskyi.studying.multithreading.Container;
import com.malskyi.studying.multithreading.ContainerStatus;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Results:
 *  Initialized: 18 containers
 *  Built: 18 containers
 *  Deployed: 18 containers
 */
public class WorkersDemo {
    private static final int THREADS_COUNT = 3;

    private static final long INIT_DELAY = 100L;
    private static final long BUILD_DELAY = 300L;
    private static final long DEPLOY_DELAY = 600L;

    private static final Queue<Container> INITIALIZED_CONTAINERS = new ConcurrentLinkedQueue<>();
    private static final Queue<Container> BUILT_CONTAINERS = new ConcurrentLinkedQueue<>();
    private static final Queue<Container> DEPLOYED_CONTAINERS = new ConcurrentLinkedQueue<>();

    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    public static void main(String[] args) throws InterruptedException {
        final List<Worker> workers = Stream.generate(Worker::new)
                .limit(THREADS_COUNT)
                .toList();

        System.out.println("Starting workers...");
        workers.forEach(Thread::start);

        Thread.sleep(6000L);

        workers.forEach(Thread::interrupt);

        Thread.sleep(300L);
        System.out.println("Results:");
        System.out.println("\tInitialized: " + INITIALIZED_CONTAINERS.size() + " containers");
        System.out.println("\tBuilt: " + BUILT_CONTAINERS.size() + " containers");
        System.out.println("\tDeployed: " + DEPLOYED_CONTAINERS.size() + " containers");

        System.exit(0);
    }

    @Data
    private static final class Worker extends Thread {
        @Override
        @SneakyThrows
        public void run() {
            System.out.printf("[%s] Starting process...%n", super.getName());
            while (!Thread.interrupted()) {
                System.out.printf("[%s] Initializing container...%n", Thread.currentThread().getName());
                final Container container = new Container(COUNTER.getAndIncrement());
                try {
                    Thread.sleep(INIT_DELAY);
                } catch (InterruptedException e) {
                    System.out.printf("[%s] InterruptedException during container initialization%n", getClass().getSimpleName());
                    System.out.printf("[%s] Stopping initialization process!%n", getClass().getSimpleName());
                    break;
                }
                container.setInitializedBy(String.format("[%s] %s ", Thread.currentThread().getName(), getClass().getSimpleName()));
                container.setContainerStatus(ContainerStatus.INITIALIZED);
                INITIALIZED_CONTAINERS.add(container);

                System.out.printf("[%s] Building %s...%n", getClass().getSimpleName(), container.getName());
                try {
                    sleep(BUILD_DELAY);
                } catch (InterruptedException e) {
                    System.out.printf("[%s] InterruptedException caught during build%n", getClass().getSimpleName());
                    System.out.printf("[%s] Finishing process without completion%n", getClass().getSimpleName());
                    break;
                }
                container.setBuildBy(String.format("[%s] %s ", Thread.currentThread().getName(), getClass().getSimpleName()));
                container.setContainerStatus(ContainerStatus.BUILT);
                BUILT_CONTAINERS.add(container);

                System.out.printf("[%s] Deploying %s...%n", getClass().getSimpleName(), container.getName());
                try {
                    Thread.sleep(DEPLOY_DELAY);
                } catch (InterruptedException e) {
                    System.out.printf("[%s] InterruptedException caught inside deploy worker during deployment finish!%n", getClass().getSimpleName());
                    System.out.printf("[%s] Currently deployed %s containers%n", getClass().getSimpleName(), DEPLOYED_CONTAINERS.size());
                    System.out.printf("[%s] Finishing deployment process!%n", getClass().getSimpleName());
                    break;
                } finally {
                    container.setDeployedBy(String.format("[%s] %s ", Thread.currentThread().getName(), getClass().getSimpleName()));
                    container.setContainerStatus(ContainerStatus.DEPLOYED);
                    DEPLOYED_CONTAINERS.add(container);
                }
            }
        }

    }
}
