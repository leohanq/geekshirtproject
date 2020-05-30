package com.geekshirt.orderservice.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekshirt.orderservice.dto.AccountDto;
import com.geekshirt.orderservice.dto.CustomerDto;
import com.geekshirt.orderservice.dto.ShipmentOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ShippingOrderProducer {
    @Autowired
    private RabbitTemplate template;

    @Qualifier(value = "outbound")
    private Queue queue;

    public void send(String orderId, AccountDto account) {
        ShipmentOrderRequest shipmentRequest = new ShipmentOrderRequest();

        ObjectMapper objectMapper = new ObjectMapper();
        try {

            CustomerDto customer = account.getCustomer();
            String shipmentReceiver = customer.getLastName() + ", " + customer.getFirstName();

            shipmentRequest.setOrderId(orderId);
            shipmentRequest.setName(shipmentReceiver);
            shipmentRequest.setReceiptEmail(customer.getEmail());
            shipmentRequest.setShippingAddress(account.getAddress());

            Message jsonMessage = MessageBuilder.withBody(objectMapper.writeValueAsString(shipmentRequest).getBytes())
                    .andProperties(MessagePropertiesBuilder.newInstance().setContentType("application/json")
                            .build()).build();

            this.template.convertAndSend("INBOUND_SHIPMENT_ORDER", jsonMessage);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        log.debug(" [x] Sent '" + shipmentRequest.toString() + "'");
    }
}