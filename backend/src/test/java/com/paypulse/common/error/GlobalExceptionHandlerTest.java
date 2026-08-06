package com.paypulse.common.error;

import com.paypulse.payment.service.PaymentException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

    @MockBean
    private io.github.bucket4j.distributed.proxy.ProxyManager<byte[]> proxyManager;

	@RestController
	static class ThrowingController {
		@GetMapping("/test/payment")
		String paymentError() {
			throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AMOUNT, "amount is invalid");
		}

		@GetMapping("/test/runtime")
		String runtimeError() {
			throw new IllegalStateException("boom");
		}
	}

	@Test
	void payment_exception_is_mapped_to_api_error() throws Exception {
		mockMvc.perform(get("/test/payment"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_AMOUNT"))
				.andExpect(jsonPath("$.message").value("amount is invalid"))
				.andExpect(jsonPath("$.path").value("/test/payment"));
	}

	@Test
	void generic_exception_does_not_leak_stacktrace() throws Exception {
		mockMvc.perform(get("/test/runtime"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errorCode").value("PROCESSING_ERROR"))
				.andExpect(content().string(not(containsString("IllegalStateException"))))
				.andExpect(content().string(not(containsString("boom"))));
	}
}


