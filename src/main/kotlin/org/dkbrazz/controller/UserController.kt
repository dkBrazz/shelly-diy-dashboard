package org.dkbrazz.controller

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user")
class UserController {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: OAuth2User?): UserInfo? {
        if (principal == null) return null
        
        val email = principal.attributes["email"] as? String
        val name = principal.attributes["name"] as? String
        val picture = principal.attributes["picture"] as? String
        
        return UserInfo(email, name, picture)
    }

    data class UserInfo(
        val email: String?,
        val name: String?,
        val picture: String?
    )
}
