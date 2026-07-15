package es.boffmedia.teras.mcef;

import org.cef.callback.CefQueryCallback;

/**
 * Mirror of the montoyo {@code IJSQueryCallback} used by the 1.16.5 mod, so the ported
 * {@code QueryHelper}/{@code ScreenshotHandler} dispatch code stays almost identical.
 *
 * <p>Backed by JCEF's {@link CefQueryCallback}, which is what CinemaMod MCEF exposes for
 * the {@code window.mcefQuery(...)} bridge. A {@code success(json)} resolves the JS
 * promise's {@code onSuccess}; {@code failure(code,msg)} resolves {@code onFailure}.</p>
 */
public interface JsQueryCallback {
    void success(String response);

    void failure(int errorCode, String errorMessage);

    /** Adapts a JCEF {@link CefQueryCallback} to this interface. */
    static JsQueryCallback of(CefQueryCallback cef) {
        return new JsQueryCallback() {
            @Override
            public void success(String response) {
                cef.success(response == null ? "" : response);
            }

            @Override
            public void failure(int errorCode, String errorMessage) {
                cef.failure(errorCode, errorMessage == null ? "" : errorMessage);
            }
        };
    }
}
