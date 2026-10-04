package com.yulinlin.data.lang.util;

import java.util.concurrent.ThreadLocalRandom;

public class RandomUtil {

    public static ThreadLocalRandom getRandom() {
        return ThreadLocalRandom.current();
    }

    public static int randomInt(int min, int max) {

        if(max <=0 ){
            return 0;
        }

        return getRandom().nextInt(min, max);
    }

    public static int randomInt() {
        return getRandom().nextInt();
    }

    public static int randomInt(int limit) {
        if(limit <=0 ){
            return 0;
        }
        return getRandom().nextInt(limit);
    }

    public static char randomChar() {
        return randomChar("abcdefghijklmnopqrstuvwxyz0123456789");
    }

    public static char randomChar(String baseString) {
        return baseString.charAt(randomInt(baseString.length()));
    }


    public static void main(String[] args) {
        randomInt(0,0);
    }


}
