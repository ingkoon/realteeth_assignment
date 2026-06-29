package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyProviderTest {

    @Test
    void get_issuesKeyOnce_andCaches() {
        MockWorkerClient client = Mockito.mock(MockWorkerClient.class);
        when(client.issueKey()).thenReturn("mock_key");
        ApiKeyProvider provider = new ApiKeyProvider(client);

        String first = provider.get();
        String second = provider.get();

        assertThat(first).isEqualTo("mock_key");
        assertThat(second).isEqualTo("mock_key");
        verify(client, times(1)).issueKey();
    }

    @Test
    void invalidate_forcesReissue() {
        MockWorkerClient client = Mockito.mock(MockWorkerClient.class);
        when(client.issueKey()).thenReturn("k1", "k2");
        ApiKeyProvider provider = new ApiKeyProvider(client);

        assertThat(provider.get()).isEqualTo("k1");
        provider.invalidate();
        assertThat(provider.get()).isEqualTo("k2");
        verify(client, times(2)).issueKey();
    }
}
