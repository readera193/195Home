package com.family195home.app.family.application;

/**
 * family 模組對外公開的唯一存取入口（port）：其他模組只需知道「呼叫者是否為群組成員／管理者」
 * 與「群組目前狀態」，不應依賴 family 的 domain 型別或 service 實作（見 research.md 決策 7）。
 */
public interface FamilyAccess {

    /** 呼叫者須為該群組在職成員，否則拋出 ApiException。 */
    MemberRef requireMember(Long familyGroupId, Long callerUserId);

    /** 呼叫者須為該群組在職管理者，否則拋出 ApiException。 */
    MemberRef requireAdmin(Long familyGroupId, Long callerUserId);

    GroupState groupState(Long familyGroupId);

    record MemberRef(Long memberId) {
    }

    enum GroupState {
        ACTIVE,
        DISSOLVED,
        NOT_FOUND
    }
}
