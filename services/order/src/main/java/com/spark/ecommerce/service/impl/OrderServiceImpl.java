package com.spark.ecommerce.service.impl;

import com.spark.ecommerce.customer.CustomerClient;
import com.spark.ecommerce.dto.OrderLineRequest;
import com.spark.ecommerce.dto.OrderRequest;
import com.spark.ecommerce.dto.PurchaseRequest;
import com.spark.ecommerce.entity.OrderLine;
import com.spark.ecommerce.exception.BusinessException;
import com.spark.ecommerce.kafka.OrderConfirmation;
import com.spark.ecommerce.kafka.OrderProducer;
import com.spark.ecommerce.product.ProductClient;
import com.spark.ecommerce.repository.OrderRepository;
import com.spark.ecommerce.service.IOrderLineService;
import com.spark.ecommerce.service.IOrderService;
import com.spark.ecommerce.util.OrderMapper;
import jakarta.ws.rs.core.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements IOrderService {

    @Autowired
    private CustomerClient customerClient;

    @Autowired
    private ProductClient productClient;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private IOrderLineService orderLineService;

    @Autowired
    private OrderProducer orderProducer;

    @Override
    public Integer createOrder(OrderRequest request) {

        //check the customer - (Feign Client)
        var customer = customerClient.findByCustomerById(request.customerId())
                .orElseThrow(() -> new BusinessException("Cannot create order:: No customer exists with provided ID"));


        //purchase the products --> product-ms (Rest Template)
        var purchasedProducts =  this.productClient.purchaseProducts(request.products());

        //persist order
        var order = orderRepository.save(orderMapper.toOrder(request));

        //persist order lines
        for(PurchaseRequest purchaseRequest: request.products()) {
            orderLineService.saveOrderLine(
                    new OrderLineRequest(
                            null,
                            order.getId(),
                            purchaseRequest.productId(),
                            purchaseRequest.quantity()
                    )
            );
        }


        //start payment process

        //send the order confirmation --> notification-ms(kafka)
        orderProducer.sendOrderConfirmation(
                new OrderConfirmation(
                request.reference(),
                request.amount(),
                request.paymentMethod(),
                customer,
                purchasedProducts
                )
        );

        return order.getId();
    }
}
