package com.yulinlin.data.lang.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

@Slf4j
public class ThreadDelayUtil extends Thread  {

    private static class Node implements Delayed {
        Runnable runnable;

        private long runTime;

        public Node(Runnable runnable, long runTime) {
            this.runnable = runnable;
            this.runTime = runTime;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            //判断avaibleTime是否大于当前系统时间，并将结果转换成MILLISECONDS
            long diffTime= runTime- System.currentTimeMillis();
            return unit.convert(diffTime,TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            //compareTo用在DelayedUser的排序
            return (int)(this.runTime - ((Node)o).runTime);
        }

    }

    private static   DelayQueue<Node> delayQueue = new DelayQueue();

    @Override
    public void run() {
        while (true){
            try {
                Node element = delayQueue.take();
                element.runnable.run();
            }catch (Exception e){
                log.error("任务消费异常",e);
            }
        }

    }

   static volatile boolean init = false;



    public static void  submit(Runnable task,int delay) {
        Node node = new Node(task,System.currentTimeMillis()+delay);
        delayQueue.add(node);
        if(init == false){
            synchronized (ThreadDelayUtil.class){
                if(init == false){
                    init = true;
                    ExecutorService executor = Executors.newFixedThreadPool(2);
                    executor.submit(new ThreadDelayUtil());
                    executor.submit(new ThreadDelayUtil());
                }
            }

        }
    }



    public static void main(String[] args) throws Exception {
        submit(() -> {
            System.out.println(1);
        },1000);

        submit(() -> {
            System.out.println(2);
        },2000);
        submit(() -> {
            System.out.println(3);
        },3000);

        Thread.sleep(20*1000);
    }

}
