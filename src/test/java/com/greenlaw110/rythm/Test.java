package com.greenlaw110.rythm;

import com.greenlaw110.rythm.utils.JSONWrapper;
import com.greenlaw110.rythm.utils.S;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 3/02/13
 * Time: 8:28 AM
 * To change this template use File | Settings | File Templates.
 */
public class Test {
    public static void main(String[] args) {
        String s = "abc xyz";
        System.out.println(S.capitalizeWords(s));
        System.out.println(S.capAll(s));
        System.out.println(S.capFirst(s));
    }
}
