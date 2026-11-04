package com.dc.medapp.medappbe.news;

import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.news.dto.CreateNewsRequest;
import com.dc.medapp.medappbe.security.ProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsService {

    private static final int MAX_DAYS_AHEAD = 365;

    private final NewsRepository newsRepo;
    private final DoctorRepository doctorRepo;
    private final ProfileResolver profileResolver;

    @Transactional
    @Audited(action = "CREATE_NEWS", entity = "News")
    public NewsEntity create(CreateNewsRequest req, Long docId){
        var today = LocalDate.now();
        if(req.expiresAt().isBefore(today)){
            throw new IllegalArgumentException("La data di scadenza non può essere nel passato.");
        }
        if(req.expiresAt().isAfter(today.plusDays(MAX_DAYS_AHEAD))){
            throw new IllegalArgumentException("La scadenza può essere al massimo tra " + MAX_DAYS_AHEAD + " giorni.");
        }
        var author = doctorRepo.findById(docId).orElseThrow(()-> new ResourceNotFoundException("Medico "+docId+" non trovato."));
        return newsRepo.save(
                NewsEntity.builder()
                        .title(req.title().trim())
                        .body(req.body().trim())
                        .expiresAt(req.expiresAt())
                        .author(author)
                        .build());
    }

    @Transactional(readOnly = true)
    public List<NewsEntity> findActive(){
        return newsRepo.findByExpiresAtGreaterThanEqualOrderByCreatedAtDescIdDesc(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<NewsEntity> findByAuthor(Long authorId){
        return newsRepo.findByAuthorIdOrderByCreatedAtDescIdDesc(authorId);
    }

    @Transactional
    @Audited(action = "DELETE_NEWS", entity = "News", idArg = 1)
    public void delete(UserEntity actor, Long newsId){
        var news = newsRepo.findById(newsId).orElseThrow(() -> new ResourceNotFoundException("La news " + newsId + " non è stata trovata."));
        if(actor.getRole() != Role.ADMIN && !news.getAuthor().getId().equals(profileResolver.doctorIdOf(actor))){
            throw new UnauthorizedAccessException("Solo l'autore o l'amministratore possono cancellare la news.");
        }
        if(news.isAutomatic()){
            throw new IllegalArgumentException("Questa news è collegata ad un'assenza: annulla l'assenza per toglierla.");
        }
        newsRepo.delete(news);
    }

}
