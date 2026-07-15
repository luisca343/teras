package es.boffmedia.teras.mcef;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.QueryHelper;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

/**
 * Bridges {@code window.mcefQuery({request, onSuccess, onFailure})} calls made by the
 * SmartRotom web app into Java, reproducing the montoyo {@code IJSQueryHandler} contract
 * on top of JCEF's message router — without modifying MCEF.
 */
public class TerasQueryRouter extends CefMessageRouterHandlerAdapter {

    @Override
    public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                           String request, boolean persistent, CefQueryCallback callback) {
        try {
            return QueryHelper.handleQuery(browser, queryId, request, persistent, JsQueryCallback.of(callback));
        } catch (Exception e) {
            Teras.LOGGER.error("Error handling JS query: {}", request, e);
            callback.failure(-1, "Internal error: " + e.getMessage());
            return true;
        }
    }

    @Override
    public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
        Teras.LOGGER.info("JS query {} canceled", queryId);
    }
}
