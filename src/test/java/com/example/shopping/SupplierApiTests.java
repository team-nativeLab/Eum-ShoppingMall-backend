package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** 발주처 관리와 상품의 발주처 지정(#208). 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class SupplierApiTests {

	private static final String SUPPLIER = """
			{"companyName":" 세이프산업 ","contactName":"김담당","contactPhone":"02-1234-5678",
			 "contactEmail":"sales@safe.example","orderChannel":"EMAIL","orderEmail":"order@safe.example",
			 "orderFax":"02-9999-9999","address":"경기도 안산시","memo":""}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 발주처를_등록_수정하고_수신_방식에_맞지_않는_값은_저장하지_않는다() throws Exception {
		long id = idOf(create(SUPPLIER)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.companyName").value("세이프산업"))
				.andExpect(jsonPath("$.data.orderEmail").value("order@safe.example"))
				// 이메일로 받으므로 팩스 번호는 저장하지 않는다.
				.andExpect(jsonPath("$.data.orderFax").doesNotExist()));

		mockMvc.perform(put("/api/v1/admin/suppliers/{id}", id).contentType(MediaType.APPLICATION_JSON)
						.content(SUPPLIER.replace("\"EMAIL\"", "\"FAX\"")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.orderChannel").value("FAX"))
				.andExpect(jsonPath("$.data.orderFax").value("02-9999-9999"))
				.andExpect(jsonPath("$.data.orderEmail").doesNotExist());
		mockMvc.perform(get("/api/v1/admin/suppliers"))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].contactName").value("김담당"));
	}

	@Test
	void 수신_방식에_필요한_주소가_없거나_형식이_틀리면_거절한다() throws Exception {
		create(SUPPLIER.replace("\"order@safe.example\"", "\"\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SUPPLIER_ORDER_CONTACT_REQUIRED"));
		create(SUPPLIER.replace("\"EMAIL\"", "\"FAX\"").replace("\"02-9999-9999\"", "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SUPPLIER_ORDER_CONTACT_REQUIRED"));
		create(SUPPLIER.replace("\"02-1234-5678\"", "\"전화번호\"")).andExpect(status().isBadRequest());
		create(SUPPLIER.replace("\"sales@safe.example\"", "\"sales\"")).andExpect(status().isBadRequest());
		create(SUPPLIER.replace("\"EMAIL\"", "\"POST\"")).andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/v1/admin/suppliers/{id}", 999_999).contentType(MediaType.APPLICATION_JSON)
						.content(SUPPLIER))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"));
	}

	@Test
	void 상품에_발주처를_지정하고_연결된_발주처는_지울_수_없다() throws Exception {
		long supplier = idOf(create(SUPPLIER));
		long other = idOf(create(SUPPLIER.replace("세이프산업", "워크핏")));
		String product = mockMvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"ABS 안전모","basePrice":30000,"supplierId":%d,
								 "skus":[{"optionName":"흰색","price":30000,"stock":10}]}
								""".formatted(supplier)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.supplierId").value(supplier))
				.andReturn().getResponse().getContentAsString();
		long productId = ((Number) JsonPath.read(product, "$.data.id")).longValue();

		mockMvc.perform(delete("/api/v1/admin/suppliers/{id}", supplier))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SUPPLIER_IN_USE"));
		// 수정에서 다른 발주처로 바꾸면 이전 발주처를 지울 수 있다.
		mockMvc.perform(patch("/api/v1/admin/products/{id}", productId).contentType(MediaType.APPLICATION_JSON)
						.content("{\"supplierId\":" + other + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.supplierId").value(other));
		mockMvc.perform(delete("/api/v1/admin/suppliers/{id}", supplier)).andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/admin/products/{id}", productId).contentType(MediaType.APPLICATION_JSON)
						.content("{\"supplierId\":999999}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/admin/products").param("keyword", "ABS"))
				.andExpect(jsonPath("$.data.content[0].supplierId").value(other));
		// 고객 응답에는 발주처가 없다.
		String customer = mockMvc.perform(get("/api/v1/products/{id}", productId).with(anonymous()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(customer).doesNotContain("supplier");
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/admin/suppliers").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
				.longValue();
	}
}
