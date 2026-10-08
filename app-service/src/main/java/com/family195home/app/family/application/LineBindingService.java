package com.family195home.app.family.application;

import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.LineBinding;
import com.family195home.app.family.domain.LineBindingCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LINE 帳號與家庭成員身分綁定（US5、US6）。碼的產生與消費皆在 app-service 內完成，
 * notification-service 透過 contracts/app-service.md 的內部 API 呼叫本服務的邏輯。
 */
@Service
public class LineBindingService {

    private static final String NUMERIC_ALPHABET = "0123456789";
    private static final int CODE_LENGTH = 6;

    private final LineBindingCodeRepository codeRepository;
    private final LineBindingRepository bindingRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final long ttlMinutes;
    private final SecureRandom random = new SecureRandom();

    public LineBindingService(
            LineBindingCodeRepository codeRepository,
            LineBindingRepository bindingRepository,
            FamilyMemberRepository familyMemberRepository,
            @Value("${app.line-binding-code.ttl-minutes}") long ttlMinutes) {
        this.codeRepository = codeRepository;
        this.bindingRepository = bindingRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.ttlMinutes = ttlMinutes;
    }

    // FR-023：限本人產生自己的綁定碼
    public LineBindingCode generateCodeForCaller(Long familyMemberId, Long callerUserId) {
        FamilyMember member = familyMemberRepository.findById(familyMemberId)
                .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "MEMBER_NOT_FOUND", "找不到成員"));
        if (!member.getUserId().equals(callerUserId)) {
            throw new ApiException(ErrorKind.FORBIDDEN, "NOT_SELF", "僅能為自己產生綁定碼");
        }
        return generateCode(familyMemberId);
    }

    // FR-023：成員登入後產生一組專屬綁定碼，10 分鐘內有效
    public LineBindingCode generateCode(Long familyMemberId) {
        LocalDateTime now = LocalDateTime.now();
        LineBindingCode code = new LineBindingCode(familyMemberId, randomNumericCode(), now.plusMinutes(ttlMinutes), false, now);
        codeRepository.save(code);
        return code;
    }

    // FR-020, FR-023：驗證碼有效性與單次使用；LINE 帳號重複綁定拒絕
    @Transactional
    public BindingResult consume(String code, String lineUserId) {
        LineBindingCode bindingCode = codeRepository.findByCode(code)
                .filter(c -> c.isValidAt(LocalDateTime.now()))
                .orElseThrow(() -> new ApiException(ErrorKind.GONE, "CODE_EXPIRED_OR_USED", "綁定碼已過期或已使用，請重新登入平台產生新碼"));

        if (bindingRepository.existsByLineUserId(lineUserId)) {
            throw new ApiException(ErrorKind.CONFLICT, "LINE_ACCOUNT_ALREADY_BOUND", "此 LINE 帳號已綁定其他家庭成員身分");
        }

        codeRepository.markUsed(bindingCode.getId());
        LineBinding binding = new LineBinding(bindingCode.getFamilyMemberId(), lineUserId, LocalDateTime.now());
        bindingRepository.save(binding);

        Long familyGroupId = requireMember(bindingCode.getFamilyMemberId()).getFamilyGroupId();
        return new BindingResult(bindingCode.getFamilyMemberId(), familyGroupId);
    }

    // FR-015：供 LINE 關鍵字查詢確認發話帳號是否已綁定
    @Transactional(readOnly = true)
    public Optional<BindingResult> findByLineUserId(String lineUserId) {
        return bindingRepository.findByLineUserId(lineUserId)
                .map(binding -> new BindingResult(binding.getFamilyMemberId(), requireMember(binding.getFamilyMemberId()).getFamilyGroupId()));
    }

    // 供每月排程通知取得所有家庭的所有 LINE 綁定
    @Transactional(readOnly = true)
    public List<BoundMember> findAllBoundMembers() {
        return bindingRepository.findAll().stream()
                .map(binding -> new BoundMember(
                        binding.getFamilyMemberId(),
                        familyMemberRepository.findById(binding.getFamilyMemberId()).map(FamilyMember::getFamilyGroupId).orElse(null),
                        binding.getLineUserId()))
                .toList();
    }

    private FamilyMember requireMember(Long familyMemberId) {
        return familyMemberRepository.findById(familyMemberId)
                .orElseThrow(() -> new ApiException(ErrorKind.NOT_FOUND, "MEMBER_NOT_FOUND", "找不到成員"));
    }

    private String randomNumericCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(NUMERIC_ALPHABET.charAt(random.nextInt(NUMERIC_ALPHABET.length())));
        }
        return sb.toString();
    }

    public record BindingResult(Long familyMemberId, Long familyGroupId) {
    }

    public record BoundMember(Long familyMemberId, Long familyGroupId, String lineUserId) {
    }
}
