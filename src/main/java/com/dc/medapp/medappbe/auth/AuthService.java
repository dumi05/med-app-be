package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.auth.dto.AuthResponse;
import com.dc.medapp.medappbe.auth.dto.ChangePasswordRequest;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import com.dc.medapp.medappbe.common.exception.*;
import com.dc.medapp.medappbe.common.validation.EmailPolicy;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    //  A valid BCrypt hash of a password that nobody knows. It is compared when the e-mail is unknown, so that the time
    //  of the answer does not tell whether the account exists.
    private static final String TIMING_DUMMY_HASH = "$2a$10$zF.iqTrlLleRax9IaGjgw.OpzjsiifFQBSC4QWpOk.gTzSzbBeT3y";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordPolicy passwordPolicy;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public AuthResponse register (RegisterRequest request) {

        if(!request.gdprConsent()) {
            throw new IllegalArgumentException("Il consenso GDPR è obbligatorio per la registrazione.");
        }

        var email = EmailPolicy.normalize(request.email());
        passwordPolicy.requireValid(request.password(), email, request.firstName(), request.lastName());

        if(userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("L'email è già in uso.");
        }

        var user = UserEntity.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.PATIENT)
                .build();

        userRepository.save(user);
        var patient = PatientEntity.builder()
                .user(user)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .birthDate(request.birthDate())
                .gdprConsent(true)
                .gdprConsentVersion(request.gdprConsentVersion())
                .gdprConsentTimestamp(Instant.now())
                .anonymized(false)
                .build();

        patientRepository.save(patient);

        return responseFor(user);
    }

    public AuthResponse login (LoginRequest request) {
        var email = EmailPolicy.normalize(request.email());
        loginAttemptService.remainingLock(email).ifPresent(rem -> {
            throw new AccountLockedException(rem);
        });
        var user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        boolean passwordMatches = passwordMatches(request.password(), user == null ? TIMING_DUMMY_HASH : user.getPasswordHash());
        if(user == null || !user.isEnabled() || !passwordMatches) {
            loginAttemptService.recordFailure(email);
            throw new InvalidCredentialsException("Credenziali non valide");
        }

        loginAttemptService.recordSuccess(email);
        return responseFor(user);
    }

    @Transactional
    @Audited(action = "CHANGE_PASSWORD", entity = "User")
    public AuthResponse changePassword(UserEntity principal, ChangePasswordRequest req){
        var user = userRepository.findById(principal.getId()).orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
        var email = EmailPolicy.normalize(user.getEmail());

        loginAttemptService.remainingLock(email).ifPresent(rem -> {
            throw new AccountLockedException(rem);
        });
        if(!passwordMatches(req.currentPassword(), user.getPasswordHash())){
            loginAttemptService.recordFailure(email);
            throw new InvalidCurrentPasswordException();
        }

        var names = namesOf(user);
        passwordPolicy.requireValid(req.newPassword(), user.getEmail(), names.first(), names.last());
        if(passwordEncoder.matches(req.newPassword(), user.getPasswordHash())){
            throw new WeakPasswordException(List.of("La nuova password deve essere diversa da quella attuale."));
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setMustChangePassword(false);
        loginAttemptService.recordSuccess(email);
        return responseFor(user);
    }

    private AuthResponse responseFor(UserEntity user) {
        return new AuthResponse(jwtService.generateToken(user), user.getEmail(), user.getRole(), user.isMustChangePassword());
    }

    private boolean passwordMatches(String password, String hash) {
        return password.getBytes(StandardCharsets.UTF_8).length <= PasswordPolicy.MAX_BYTES
                && passwordEncoder.matches(password, hash);
    }

    private Names namesOf(UserEntity user){
        return switch (user.getRole()) {
            case PATIENT -> patientRepository.findByUserId(user.getId())
                    .map(pat -> new Names(pat.getFirstName(), pat.getLastName())).orElse(Names.NONE);
            case DOCTOR -> doctorRepository.findByUserId(user.getId())
                    .map(doc -> new Names(doc.getFirstName(), doc.getLastName())).orElse(Names.NONE);
            default -> Names.NONE;
        };
    }

    private record Names (String first, String last){
        static final Names NONE = new Names(null, null);
    }
}
