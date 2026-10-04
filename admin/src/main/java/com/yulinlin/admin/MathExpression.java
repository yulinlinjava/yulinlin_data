package com.yulinlin.admin;

import java.util.*;

public class MathExpression {


    private String expression;


    Node root;

    private Map<String,Integer> priorityMap;

    {
        priorityMap = new HashMap<>();
        priorityMap.put("+",0);
        priorityMap.put("-",0);
        priorityMap.put("*",1);
        priorityMap.put("/",1);
    }

    public MathExpression(String expression) {
        this.expression = expression;
        init();
    }

    private boolean isOperateSymbol(char c){
        return priorityMap.containsKey(c+"");
    }

    private void init(){
        LinkedList<Node>   numbers = new LinkedList<>();
        LinkedList<Node>   symbols = new LinkedList<>();

        String sb ="";
        for (char c : expression.toCharArray()) {
            if(isOperateSymbol(c)){
                numbers.add(new Node(sb,true));
                symbols.add(new Node(c+"",false));
                sb="";
            }else {
                sb+=c;
            }
        }
        numbers.add(new Node(sb,true));
        parse(numbers,symbols);
    }


    private boolean compare(String a,String b){
        int p =  priorityMap.get(a) - priorityMap.get(b);
        return p>=0?true:false;
    }

    private void parse(  LinkedList<Node>   numberList,LinkedList<Node>   symbolList){
        LinkedList<Node>   numbers = new LinkedList<>();
        LinkedList<Node>   symbols = new LinkedList<>();


        while (numbers.size() != 1 ){
            if(symbols.isEmpty()){
                Node n1 = numberList.removeFirst();
                Node o1 = symbolList.removeFirst();
                Node n2 = numberList.removeFirst();
                numbers.addFirst(n2);
                numbers.addFirst(n1);
                symbols.addFirst(o1);
            }
            Node o1 = symbols.removeFirst();
            Node n1 = numbers.removeFirst();
            Node n2 = numbers.removeFirst();

            if(!symbolList.isEmpty()){
                Node o2 = symbolList.removeFirst();
                if(compare(o1.key,o2.key)){
                    o1.left = n1;
                    o1.right = n2;
                    numbers.addFirst(o1);
                    symbolList.addFirst(o2);
                }else {
                    Node n3 = numbers.removeFirst();
                    o2.left = n2;
                    o2.right = n3;
                    numbers.addFirst(o2);
                    numbers.addFirst(n1);
                    symbols.addFirst(o1);
                }
            }else {
                o1.left = n1;
                o1.right = n2;

                numbers.addFirst(o1);
            }



         /*   Node n1 = numbers.removeFirst();
            Node n2 = numbers.removeFirst();
            Node o1 = symbols.removeFirst();

            if(symbols.size() > 0){
                Node o2 = symbols.removeFirst();

                if(compare(o1.key,o2.key)){
                    o1.left = n1;
                    o1.right = n2;
                    numbers.addFirst(o1);
                    symbols.addFirst(o2);
                }else {
                    Node n3 = numbers.removeFirst();
                    o2.left = n2;
                    o2.right = n3;
                    numbers.addFirst(o2);
                    numbers.addFirst(n1);
                    symbols.addFirst(o1);
                }
            }else {
                o1.left = n1;
                o1.right = n2;

                numbers.addFirst(o1);
            }*/
        }

        root = numbers.get(0);


    }

    public void print(){
        print(root);
        System.out.println();
    }
    public void print(Node n){
        if(n == null){
            return;
        }
        print(n.left);
        System.out.print(n.key);
        print(n.right);

    }
    private Node buildNodeLeft(String a,String c){
        Node p=  new Node(c,false);
        p.left = new Node(a,true);
        return p;
    }
    private Node buildNodeRight(String a,String c){
        Node p=  new Node(c,false);
        p.right = new Node(a,true);
        return p;
    }

    private Node buildNode(String a,String b,String c){
        Node p=  new Node(c,false);
        p.left = new Node(a,true);
        p.right = new Node(b,true);
        return p;
    }

    private static class  Node{
        String key;

        boolean isNumber;

        Node left;

        Node right;

        public Node(String key, boolean isNumber) {
            this.key = key;
            this.isNumber = isNumber;
        }

        @Override
        public String toString() {
            return key;
        }
    }

    public static void main(String[] args) {
        MathExpression mathExpression = new MathExpression("1+2+3*4");
        mathExpression.print();
    }
}
