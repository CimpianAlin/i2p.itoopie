<%@page contentType="text/plain" %>
<jsp:useBean id="helper" class="net.i2p.router.web.StatHelper" />
<jsp:setProperty name="helper" property="peer" value="<%=request.getParameter("peer")%>" />
<jsp:getProperty name="helper" property="profile" />