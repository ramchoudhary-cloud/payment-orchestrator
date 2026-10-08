package com.ram.payment_orchestrator.domain.provider.adapter;

import com.ram.payment_orchestrator.domain.provider.domain.ChargeRequest;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeResult;
import com.ram.payment_orchestrator.domain.provider.domain.StatusResult;

public interface PaymentProviderAdapter {

    String providerCode();

    ChargeResult charge(ChargeRequest request);

    StatusResult queryStatus(String providerOperationId, String providerRef);
}
