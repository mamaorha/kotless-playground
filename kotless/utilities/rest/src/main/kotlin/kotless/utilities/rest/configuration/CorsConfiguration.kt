package kotless.utilities.rest.configuration

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
open class CorsConfiguration : OncePerRequestFilter() {
    override fun doFilterInternal(
        httpServletRequest: HttpServletRequest,
        httpServletResponse: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Add CORS headers for all requests
        httpServletResponse.addHeader("Access-Control-Allow-Origin", "*")
        httpServletResponse.addHeader("Access-Control-Allow-Headers", "*")
        httpServletResponse.addHeader("Access-Control-Allow-Methods", "*")
        httpServletResponse.addHeader("Access-Control-Max-Age", "3600")

        // Handle preflight OPTIONS request
        if (httpServletRequest.method == "OPTIONS") {
            httpServletResponse.status = HttpServletResponse.SC_OK
            return
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse)
    }
}