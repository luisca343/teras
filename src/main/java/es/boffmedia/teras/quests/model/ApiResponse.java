package es.boffmedia.teras.quests.model;

/**
 * Wungill's response envelope, reproduced so the mod is a drop-in for {@code WINGULL_API}. The
 * SmartRotom backend unwraps it as {@code response.data.data.quests}, i.e. it expects
 * {@code {success, message, data:{...}}}.
 *
 * <p>Applied to {@code /quests/all} <b>only</b>. {@code /quests/user/{uuid}} is sent bare, because the
 * backend reads {@code response.data.quests} there. That inconsistency is 1.16.5's, kept deliberately:
 * "fixing" it would silently break the live pipeline.</p>
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
