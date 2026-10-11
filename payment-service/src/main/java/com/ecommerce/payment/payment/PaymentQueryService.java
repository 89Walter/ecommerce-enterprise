package com.ecommerce.payment.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    /** Il cliente vede solo i propri pagamenti; per quelli altrui riceve 404, come per gli ordini. */
    public PaymentResponse findByOrder(Long orderId, String customerId, boolean admin) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .filter(found -> admin || found.getCustomerId().equals(customerId))
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
        return PaymentResponse.from(payment);
    }

    public Page<PaymentResponse> findAll(PaymentStatus status, Pageable pageable) {
        Page<Payment> payments = status == null
                ? paymentRepository.findAll(pageable)
                : paymentRepository.findByStatus(status, pageable);
        return payments.map(PaymentResponse::from);
    }
}
