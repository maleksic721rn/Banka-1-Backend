package com.banka1.notification.service;

import com.banka1.notification.model.CustomerDevice;
import com.banka1.notification.repository.CustomerDeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DeviceServiceTest {

    @Mock
    private CustomerDeviceRepository customerDeviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    private final Long customerId = 123L;
    private final String deviceToken = "token123";

    @Test
    public void testAddDevice_savesCustomerDevice() {
        deviceService.addDevice(customerId, deviceToken);

        ArgumentCaptor<CustomerDevice> captor = ArgumentCaptor.forClass(CustomerDevice.class);
        verify(customerDeviceRepository, times(1)).save(captor.capture());

        CustomerDevice savedDevice = captor.getValue();
        assertEquals(customerId, savedDevice.getCustomerId());
        assertEquals(deviceToken, savedDevice.getDeviceToken());
    }

    @Test
    public void testDeleteDevice_callsRepositoryDeleteByCustomerIdAndDeviceToken() {
        deviceService.deleteDevice(customerId, deviceToken);
        verify(customerDeviceRepository, times(1)).deleteByCustomerIdAndDeviceToken(customerId, deviceToken);
    }

    @Test
    public void testDeleteAllDevices_callsRepositoryDeleteByCustomerId() {
        deviceService.deleteAllDevices(customerId);
        verify(customerDeviceRepository, times(1)).deleteByCustomerId(customerId);
    }
}