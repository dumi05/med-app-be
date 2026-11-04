package com.dc.medapp.medappbe.news;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.news.dto.CreateNewsRequest;
import com.dc.medapp.medappbe.news.dto.NewsResponse;
import com.dc.medapp.medappbe.security.ProfileResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/v1/news")
@RestController
@RequiredArgsConstructor
public class NewsController {

    private final NewsService service;
    private final ProfileResolver profileResolver;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<NewsResponse> create(
            @AuthenticationPrincipal UserEntity actor,
            @Valid @RequestBody CreateNewsRequest req
            ) {
        var news = service.create(req, profileResolver.doctorIdOf(actor));
        return ResponseEntity.ok(NewsResponse.fromEntity(news));
    }

    @GetMapping
    public ResponseEntity<List<NewsResponse>> findActive(){
        return ResponseEntity.ok(service.findActive().stream().map(NewsResponse::fromEntity).toList());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<NewsResponse>> getMine(@AuthenticationPrincipal UserEntity user){
        return ResponseEntity.ok(
                service.findByAuthor(profileResolver.doctorIdOf(user))
                        .stream()
                        .map(NewsResponse::fromEntity)
                        .toList());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserEntity user, @PathVariable Long id){
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
