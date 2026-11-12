package com.myshoppingcart.order.application;

public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("the cart is empty");
    }
}
