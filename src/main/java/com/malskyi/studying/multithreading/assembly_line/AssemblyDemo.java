package com.malskyi.studying.multithreading.assembly_line;

import com.malskyi.studying.multithreading.Container;
import com.malskyi.studying.multithreading.ContainerStatus;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Results:
 *  Initialized: 59 containers
 *  Built: 19 containers
 *  Deployed: 10 containers
 */
public class AssemblyDemo {
    private static final int INIT_THREADS_COUNT = 1;
    private static final int BUILD_THREADS_COUNT = 1;
    private static final int DEPLOY_THREADS_COUNT = 1;

    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    public static void main(String[] args) throws InterruptedException {
        final BlockingQueue<Container> initializedToBuildContainers = new ArrayBlockingQueue<>(100);
        final BlockingQueue<Container> builtToDeployContainers = new ArrayBlockingQueue<>(100);

        final Queue<Container> initializedContainers = new ConcurrentLinkedQueue<>();
        final Queue<Container> builtContainers = new ConcurrentLinkedQueue<>();
        final Queue<Container> deployedContainers = new ConcurrentLinkedQueue<>();

        final List<Thread> initWorkers = Stream.generate(() ->
                        new Thread(new InitWorker(100L, initializedToBuildContainers, initializedContainers)))
                .limit(INIT_THREADS_COUNT)
                .toList();
        final List<Thread> buildWorkers = Stream.generate(() ->
                        new Thread(new BuildWorker(300L, initializedToBuildContainers, builtToDeployContainers, builtContainers)))
                .limit(BUILD_THREADS_COUNT)
                .toList();
        final List<Thread> deployWorkers = Stream.generate(() ->
                        new Thread(new DeployWorker(600L, builtToDeployContainers, deployedContainers)))
                .limit(DEPLOY_THREADS_COUNT)
                .toList();

        System.out.println("Starting initialization workers...");
        initWorkers.forEach(Thread::start);
        System.out.println("Starting build workers...");
        buildWorkers.forEach(Thread::start);
        System.out.println("Starting deploy workers...");
        deployWorkers.forEach(Thread::start);

        System.out.println("Running workers...");
        try {
            Thread.sleep(6000L);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        System.out.println("Interrupting initialization workers...");
        initWorkers.forEach(Thread::interrupt);
        System.out.println("Interrupting build workers...");
        buildWorkers.forEach(Thread::interrupt);
        System.out.println("Interrupting deploy workers...");
        deployWorkers.forEach(Thread::interrupt);
        System.out.println("All workers interrupted!");

        Thread.sleep(300L);
        System.out.println("Results:");
        System.out.println("\tInitialized: " + initializedContainers.size() + " containers");
        System.out.println("\tBuilt: " + builtContainers.size() + " containers");
        System.out.println("\tDeployed: " + deployedContainers.size() + " containers");

        System.exit(0);
    }

    private static final class InitWorker implements Runnable {
        private final long delay;
        private final BlockingQueue<Container> initializedToBuildContainers;
        private final Queue<Container> initializedContainers;

        public InitWorker(long delay, BlockingQueue<Container> initializedToBuildContainers, Queue<Container> initializedContainers) {
            this.delay = delay;
            this.initializedToBuildContainers = initializedToBuildContainers;
            this.initializedContainers = initializedContainers;
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                System.out.printf("[%s] Initializing container...%n", getClass().getSimpleName());
                final Container container = new Container(COUNTER.getAndIncrement());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    System.out.printf("[%s] InterruptedException during container initialization%n", getClass().getSimpleName());
                    System.out.printf("[%s] Stopping initialization process!%n", getClass().getSimpleName());
                    break;
                }
                container.setInitializedBy(String.format("[%s] %s ", Thread.currentThread().getName(), getClass().getSimpleName()));
                container.setContainerStatus(ContainerStatus.INITIALIZED);
                try {
                    initializedToBuildContainers.put(container);
                } catch (InterruptedException e) {
                    System.out.printf("[%s] InterruptedException during container initialization registration%n", getClass().getSimpleName());
                    System.out.printf("[%s] Container was not registered!%n", getClass().getSimpleName());
                }
                initializedContainers.add(container);
            }
        }
    }

    private static final class BuildWorker extends Thread {
        private final long delay;
        private final BlockingQueue<Container> initializedToBuildContainers;
        private final BlockingQueue<Container> builtToDeployContainers;
        private final Queue<Container> builtContainers;

        public BuildWorker(long delay, BlockingQueue<Container> initializedToBuildContainers, BlockingQueue<Container> builtToDeployContainers, Queue<Container> builtContainers) {
            this.delay = delay;
            this.initializedToBuildContainers = initializedToBuildContainers;
            this.builtToDeployContainers = builtToDeployContainers;
            this.builtContainers = builtContainers;
        }

        @Override
        public void run() {
            while (!interrupted()) {
                final Container container;
                try {
                    container = initializedToBuildContainers.take();
                } catch (InterruptedException e) {

                    throw new RuntimeException(e);
                }
                System.out.printf("[%s] Building %s...%n", getClass().getSimpleName(), container.getName());
                try {
                    sleep(delay);
                } catch (InterruptedException e) {
                    System.out.printf("[%s] InterruptedException caught during build%n", getClass().getSimpleName());
                    System.out.printf("[%s] Finishing process without completion%n", getClass().getSimpleName());
                    break;
                }
                container.setBuildBy(String.format("[%s] %s ", Thread.currentThread().getName(), getClass().getSimpleName()));
                container.setContainerStatus(ContainerStatus.BUILT);
                try {
                    System.out.println("Put " + container.getName() + " to deployment queue");
                    builtToDeployContainers.put(container);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                builtContainers.add(container);
            }
        }
    }

    private static final class DeployWorker implements Runnable {
        private final long delay;
        private final BlockingQueue<Container> builtToDeployContainers;
        private final Queue<Container> deployedContainers;

        public DeployWorker(long delay, BlockingQueue<Container> builtToDeployContainers, Queue<Container> deployedContainers) {
            this.delay = delay;
            this.builtToDeployContainers = builtToDeployContainers;
            this.deployedContainers = deployedContainers;
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                final Container container;
                try {
                    container = builtToDeployContainers.take();
                    System.out.println("Took " + container.getName() + " from deployment queue");
                } catch (InterruptedException e) {
                    System.out.printf("[%s] InterruptedException caught inside deploy worker during take()!%n", getClass().getSimpleName());
                    System.out.printf("[%s] Finishing process%n", getClass().getSimpleName());
                    break;
                }
                System.out.printf("[%s] Deploying %s...%n", getClass().getSimpleName(), container.getName());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    System.out.printf("[%s] InterruptedException caught inside deploy worker during deployment finish!%n", getClass().getSimpleName());
                    System.out.printf("[%s] Currently deployed %s containers%n", getClass().getSimpleName(), deployedContainers.size());
                    System.out.printf("[%s] Finishing deployment process!%n", getClass().getSimpleName());
                    break;
                } finally {
                    container.setDeployedBy(String.format("[%s] %s ", Thread.currentThread().getName(), getClass().getSimpleName()));
                    container.setContainerStatus(ContainerStatus.DEPLOYED);
                    deployedContainers.add(container);
                    System.out.println("Deployed " + container.getName());
                }
            }
        }
    }
}
