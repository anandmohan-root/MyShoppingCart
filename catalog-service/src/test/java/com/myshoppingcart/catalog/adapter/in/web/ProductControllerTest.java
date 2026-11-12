package com.myshoppingcart.catalog.adapter.in.web;

import com.myshoppingcart.catalog.application.Products;
import com.myshoppingcart.catalog.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    Products products;

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsProductsForAuthenticatedCaller() throws Exception {
        when(products.findAll()).thenReturn(List.of(new Product(UUID.randomUUID(), "Kettle", new BigDecimal("30.00"))));

        mvc.perform(get("/products").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kettle"));
    }
}
