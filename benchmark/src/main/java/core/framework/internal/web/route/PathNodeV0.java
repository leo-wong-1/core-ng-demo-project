package core.framework.internal.web.route;

import core.framework.internal.web.request.PathParams;
import core.framework.util.Maps;
import core.framework.util.Strings;

import java.util.Map;

import static core.framework.util.Strings.format;

/**
 * @author neo
 */
class PathNodeV0 {
    URLHandler handler;
    private Map<String, PathNodeV0> staticNodes;
    private DynamicNode dynamicNode;
    private DynamicNode wildcardNode;

    URLHandler register(String pathPattern) {
        return register(pathPattern, PathV0.parse(pathPattern).next);
    }

    URLHandler register(String pathPattern, PathV0 currentPath) {
        if (currentPath == null) {
            if (handler == null) handler = new URLHandler(pathPattern);
            return handler;
        } else if (Strings.startsWith(currentPath.value, ':')) {
            int paramIndex = currentPath.value.indexOf('(');
            int endIndex = paramIndex > 0 ? paramIndex : currentPath.value.length();
            String name = currentPath.value.substring(1, endIndex);
            boolean wildcard = paramIndex > 0;
            if (wildcard) {
                return registerWildcardNode(pathPattern, currentPath, name);
            } else {
                return registerDynamicNode(pathPattern, currentPath, name);
            }
        } else {
            if (staticNodes == null) staticNodes = Maps.newHashMap();
            PathNodeV0 staticNode = staticNodes.computeIfAbsent(currentPath.value, key -> new PathNodeV0());
            return staticNode.register(pathPattern, currentPath.next);
        }
    }

    private URLHandler registerWildcardNode(String pathPattern, PathV0 currentPath, String name) {
        if (currentPath.next != null) throw new Error("wildcard must be at end of path pattern, path=" + pathPattern);
        if (wildcardNode != null) {
            if (!Strings.equals(wildcardNode.param, name))
                throw new Error(format("found conflict dynamic pattern, path={}, param={}, conflictedParam={}", pathPattern, name, wildcardNode.param));
        } else {
            wildcardNode = new DynamicNode(name);
        }
        return wildcardNode.register(pathPattern, currentPath.next);
    }

    private URLHandler registerDynamicNode(String pathPattern, PathV0 currentPath, String name) {
        if (dynamicNode != null) {
            if (!Strings.equals(dynamicNode.param, name))
                throw new Error(format("found conflict dynamic pattern, path={}, param={}, conflictedParam={}", pathPattern, name, dynamicNode.param));
        } else {
            dynamicNode = new DynamicNode(name);
        }
        return dynamicNode.register(pathPattern, currentPath.next);
    }

    URLHandler find(String path, PathParams pathParams) {
        return find(PathV0.parse(path), pathParams);
    }

    URLHandler find(PathV0 currentPath, PathParams pathParams) {
        PathV0 nextPath = currentPath.next;
        if (nextPath == null) return handler;

        URLHandler handler = findStatic(nextPath, pathParams);
        if (handler != null) return handler;

        if (!"/".equals(nextPath.value)) {  // dynamic node should not match trailing slash
            handler = findDynamic(nextPath, pathParams);
            if (handler != null) return handler;
        }

        if (wildcardNode != null) {
            pathParams.put(wildcardNode.param, nextPath.subPath());
            return wildcardNode.handler;
        }

        return null;
    }

    private URLHandler findStatic(PathV0 nextPath, PathParams pathParams) {
        if (staticNodes != null) {
            PathNodeV0 nextNode = staticNodes.get(nextPath.value);
            if (nextNode != null) {
                return nextNode.find(nextPath, pathParams);
            }
        }
        return null;
    }

    private URLHandler findDynamic(PathV0 nextPath, PathParams pathParams) {
        if (dynamicNode != null) {
            URLHandler handler = dynamicNode.find(nextPath, pathParams);
            if (handler != null) {
                pathParams.put(dynamicNode.param, nextPath.value);
                return handler;
            }
        }
        return null;
    }

    static class DynamicNode extends PathNodeV0 {
        final String param;

        DynamicNode(String param) {
            this.param = param;
        }
    }
}
