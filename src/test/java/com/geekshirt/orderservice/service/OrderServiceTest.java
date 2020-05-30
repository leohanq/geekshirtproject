package com.geekshirt.orderservice.service;

import com.geekshirt.orderservice.client.CustomerServiceClient;
import com.geekshirt.orderservice.client.InventoryServiceClient;
import com.geekshirt.orderservice.dto.AccountDto;
import com.geekshirt.orderservice.dto.OrderRequest;
import com.geekshirt.orderservice.entities.Order;
import com.geekshirt.orderservice.exception.AccountNotFoundException;
import com.geekshirt.orderservice.exception.IncorrectOrderRequestException;
import com.geekshirt.orderservice.exception.PaymentNotAcceptedException;
import com.geekshirt.orderservice.producer.ShippingOrderProducer;
import com.geekshirt.orderservice.repositories.OrderRepository;
import com.geekshirt.orderservice.util.ExceptionMessagesEnum;
import com.geekshirt.orderservice.util.OrderPaymentStatus;
import com.geekshirt.orderservice.util.OrderServiceDataTestUtils;

import com.geekshirt.orderservice.util.OrderStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.*;

import org.mockito.MockitoAnnotations;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Optional;

@ExtendWith(SpringExtension.class)

public class OrderServiceTest{

    @InjectMocks
    private OrderService orderService;

    @Mock
    private CustomerServiceClient customerClient;

    @Mock
    private PaymentProcessorService paymentService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryServiceClient inventoryClient;

    @Mock
    private ShippingOrderProducer shippingMessageProducer;

    @BeforeEach
    void setUp() throws Exception {
        AccountDto mockAccount = OrderServiceDataTestUtils.getMockAccount( "12345678" );
        Mockito.doReturn(Optional.of(mockAccount)).when(customerClient).findAccountById(anyString());    }

    @DisplayName("Should Throw Incorrect Exception When Order Items Are Null")
    @Test
    public void shouldThrowIncorrectExceptionWhenOrderItemsAreNull() {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setAccountId("12345678");

        IncorrectOrderRequestException incorrectException = Assertions.assertThrows(IncorrectOrderRequestException.class,
                () -> orderService.createOrder( orderRequest ));

        Assertions.assertEquals(ExceptionMessagesEnum.INCORRECT_REQUEST_EMPTY_ITEM_ORDER.getValue(),
                incorrectException.getMessage());
    }

    @DisplayName("Should Throw Incorrect Exception When Order Items Are Empty")
    @Test
    public void shouldThrowIncorrectExceptionWhenOrderItemsAreEmpty() {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setAccountId("12345678");
        orderRequest.setItems( new ArrayList<>() );

        IncorrectOrderRequestException incorrectException = Assertions.assertThrows(IncorrectOrderRequestException.class,
                () -> orderService.createOrder( orderRequest ));

        Assertions.assertEquals(ExceptionMessagesEnum.INCORRECT_REQUEST_EMPTY_ITEM_ORDER.getValue(),
                incorrectException.getMessage());
    }

    @DisplayName("Should Throw Account Not Found Exception When Account Does Not Exists")
    @Test
    public void shouldThrowAccountNotFoundExceptionWhenAccountDoesNotExists() {
        OrderRequest orderRequest = OrderServiceDataTestUtils.getMockOrderRequest("12345678");

        Mockito.when(customerClient.findAccountById(anyString())).thenReturn(Optional.empty());

        AccountNotFoundException accountNotFoundException = Assertions.assertThrows(AccountNotFoundException.class,
                () -> orderService.createOrder( orderRequest ));

        Assertions.assertEquals(ExceptionMessagesEnum.ACCOUNT_NOT_FOUND.getValue(),
                accountNotFoundException.getMessage());
        Mockito.verify( customerClient).findAccountById( anyString() );
    }

    @DisplayName("Should Throw Payment Not Accepted Exception When Payment is Denied")
    @Test
    public void shouldThrowPaymentNotAcceptedExceptionWhenPaymentIsDenied() {
        OrderRequest orderRequest = OrderServiceDataTestUtils.getMockOrderRequest("12345678");

        Mockito.when(paymentService.processPayment(any(), any())).thenReturn(OrderServiceDataTestUtils
                .getMockPayment(orderRequest.getAccountId(), OrderPaymentStatus.DENIED));

        Mockito.doReturn(new Order()).when(orderRepository).save(any(Order.class));

        PaymentNotAcceptedException paymentNotAcceptedException = Assertions.assertThrows(PaymentNotAcceptedException.class,
                () -> orderService.createOrder(orderRequest));

        Assertions.assertEquals("The Credit Card added to your account was not accepted, please verify", paymentNotAcceptedException.getMessage());
        Mockito.verify(customerClient).findAccountById(anyString());
        Mockito.verify(orderRepository).save(any(Order.class));
        Mockito.verify(paymentService).processPayment(any(), any());

    }

    @DisplayName("Should Do Shipment When Payment is Approved")
    @Test
    public void shouldDoShipmentWhenPaymentIsApproved() throws PaymentNotAcceptedException {
        OrderRequest orderRequest = OrderServiceDataTestUtils.getMockOrderRequest("12345678");

        Mockito.when(paymentService.processPayment(any(), any())).thenReturn(OrderServiceDataTestUtils
                .getMockPayment(orderRequest.getAccountId(), OrderPaymentStatus.APPROVED));

        Mockito.doNothing().when( inventoryClient ).updateInventory( anyList() );

        Mockito.doNothing().when( shippingMessageProducer ).send( anyString(), any( AccountDto.class) );

        Mockito.doAnswer(element -> element.getArguments()[0]).when(orderRepository).save(any(Order.class));

        Order order = orderService.createOrder(orderRequest);

        Assertions.assertEquals( "12345678", order.getAccountId() );
        Assertions.assertEquals( Double.valueOf(1005.0), order.getTotalAmount() );
        Assertions.assertEquals( Double.valueOf(1165.8), order.getTotalAmountTax() );
        Assertions.assertEquals( Double.valueOf(160.8), order.getTotalTax() );
        Assertions.assertEquals(OrderStatus.PENDING, order.getStatus() );
        Assertions.assertEquals( 2, order.getDetails().size() );
        Assertions.assertEquals(OrderPaymentStatus.APPROVED, order.getPaymentStatus() );
        Assertions.assertNotNull( order.getTransactionDate() );

        Mockito.verify(customerClient).findAccountById(anyString());
        Mockito.verify(paymentService).processPayment(any(), any());
        Mockito.verify(inventoryClient).updateInventory(anyList());
        Mockito.verify(shippingMessageProducer).send(anyString(), any(AccountDto.class));
        Mockito.verify(orderRepository).save(any(Order.class));
    }

}
