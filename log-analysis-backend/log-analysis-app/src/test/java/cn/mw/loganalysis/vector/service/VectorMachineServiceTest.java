package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.dto.AgentRegisterRequest;
import cn.mw.loganalysis.vector.entity.VectorMachine;
import cn.mw.loganalysis.vector.mapper.VectorDeploymentMapper;
import cn.mw.loganalysis.vector.mapper.VectorMachineMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorMachineServiceTest {

    @Test
    void shouldReuseExistingMachineWhenIpAddressAlreadyExists() {
        VectorMachineMapper machineMapper = mock(VectorMachineMapper.class);
        VectorDeploymentMapper deploymentMapper = mock(VectorDeploymentMapper.class);
        VectorMachineService service = new VectorMachineService(machineMapper, deploymentMapper);

        AgentRegisterRequest request = new AgentRegisterRequest();
        request.setHostname("xsw");
        request.setIpAddress("192.168.111.129");
        request.setAgentVersion("1.0.0");
        request.setVectorVersion("0.55.0");
        request.setOsType("linux");

        VectorMachine existing = new VectorMachine();
        existing.setId("machine-1");
        existing.setName("old-name");
        existing.setHostname("old-host");
        existing.setIpAddress("192.168.111.129");

        when(machineMapper.selectByToken("token-1")).thenReturn(null);
        when(machineMapper.selectByHostname("xsw")).thenReturn(null);
        when(machineMapper.selectByIpAddress("192.168.111.129")).thenReturn(existing);

        VectorMachine result = service.findOrCreateByToken("token-1", request);

        assertThat(result).isSameAs(existing);
        assertThat(existing.getHostname()).isEqualTo("xsw");
        assertThat(existing.getAgentToken()).isEqualTo("token-1");
        verify(machineMapper).updateById(existing);
        verify(machineMapper, never()).insert(any(VectorMachine.class));
    }
}
