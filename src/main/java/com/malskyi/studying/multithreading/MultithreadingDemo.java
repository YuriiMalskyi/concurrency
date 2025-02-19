package com.malskyi.studying.multithreading;

public class MultithreadingDemo {

    public static void main(String[] args) {
        Thread thread = new Thread();
        thread.run();
        thread.interrupt();
    }

}
