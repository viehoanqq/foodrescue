package com.foodrescue.store.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodrescue.store.application.port.out.IInternalStoreRepository;
import com.foodrescue.store.application.usecase.ReadInternalStoreUseCase;
import com.foodrescue.store.domain.model.StoreInternalView;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalStoreControllerTest {
    private final IInternalStoreRepository repo = mock(IInternalStoreRepository.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new InternalStoreController(new ReadInternalStoreUseCase(repo), "shared-key", "finance-key"))
            .setControllerAdvice(new StoreError())
            .build();

    @Test
    void returnsOwnerStoreJsonWhenInternalKeyMatches() throws Exception {
        when(repo.findByOwnerId(7L)).thenReturn(Optional.of(new StoreInternalView.OwnerStore(15L, 7L, "APPROVED", 3L)));

        mvc.perform(get("/internal/stores/by-owner/7").header("X-Internal-Key", "shared-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value(15))
                .andExpect(jsonPath("$.ownerId").value(7))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void returnsNotFoundWhenOwnerMissing() throws Exception {
        when(repo.findByOwnerId(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/internal/stores/by-owner/99").header("X-Internal-Key", "shared-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidInternalKeyWithoutCallingRepository() throws Exception {
        mvc.perform(get("/internal/stores/by-owner/7").header("X-Internal-Key", "wrong-key"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repo);
    }
}
