package com.cherryoj.problemservice.api;

import com.cherryoj.problemservice.api.LanguageSettingDtos.LanguageSetting;
import com.cherryoj.problemservice.api.LanguageSettingDtos.LanguageSettingList;
import com.cherryoj.problemservice.api.LanguageSettingDtos.UpdateLanguageSettingRequest;
import com.cherryoj.problemservice.application.LanguageSettingService;
import com.cherryoj.problemservice.security.CurrentIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/admin/languages")
public class LanguageSettingController {

    private final LanguageSettingService languages;

    public LanguageSettingController(LanguageSettingService languages) {
        this.languages = languages;
    }

    @GetMapping
    ResponseEntity<LanguageSettingList> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(languages.list());
    }

    @PutMapping("/{languageId}")
    ResponseEntity<LanguageSetting> update(
            @PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{0,31}$") String languageId,
            @Valid @RequestBody UpdateLanguageSettingRequest request,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(languages.update(
                        languageId,
                        request,
                        CurrentIdentity.from(authentication).userId()));
    }
}
