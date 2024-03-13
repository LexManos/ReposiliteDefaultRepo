package net.minecraftforge.lex.reposilitedefaultrepo;

import java.io.IOException;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import com.reposilite.maven.MavenFacade;
import com.reposilite.maven.Repository;
import com.reposilite.plugin.api.Facade;
import com.reposilite.plugin.api.Plugin;
import com.reposilite.plugin.api.ReposilitePlugin;
import com.reposilite.web.api.HttpServerInitializationEvent;

import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequestWrapper;

@Plugin(name = "default-repo")
public class DefaultRepoPlugin extends ReposilitePlugin {
    private String defaultRepo;
    private Servlet servlet;
    private MavenFacade maven;

    @Override
    public @Nullable Facade initialize() {
        this.extensions().registerEvent(HttpServerInitializationEvent.class, this::onServerInit);
        return null;
    }

    public void onServerInit(HttpServerInitializationEvent event) {
        this.maven = extensions().facade(MavenFacade.class);

        this.defaultRepo = "releases";
        var cfg = System.getenv("REPOSILITE_DEFAULT_REPO");
        if (cfg != null) {
            if (maven.getRepository(cfg) == null)
                throw new IllegalStateException("Invalid REPOSILITE_DEFAULT_REPO, `" + cfg + "` could not be found in " +
                    maven.getRepositories().stream().map(Repository::getName).collect(Collectors.joining(", ")));
            this.defaultRepo = cfg;
        }

        this.servlet = event.getConfig().pvt.servlet.getValue().getServlet();
        event.getConfig().router.mount(route -> route.before("/*", this::beforeAnything));
    }

    private void beforeAnything(Context ctx) throws IOException, ServletException {
        // Only care about people requesting files, if you want to manage things use the proper repo
        if (ctx.method() != HandlerType.GET && ctx.method() != HandlerType.HEAD)
            return;

        var path = ctx.req().getRequestURI();
        var idx = path.indexOf('/', 1);
        var repo =  path.substring(1, idx == -1 ? path.length() : idx);

        if (repo.length() == 0 || isFrontend(repo) || this.maven.getRepository(repo) != null)
            return;

        var fixed = new HttpServletRequestWrapper(ctx.req()) {
            @Override
            public String getRequestURI() {
                return '/' + defaultRepo + super.getRequestURI();
            }
        };

        //System.out.println("Path:     " + ctx.path());
        //System.out.println("Redirect: " + fixed.getRequestURI());
        ctx.skipRemainingHandlers();
        servlet.service(fixed, ctx.res());
    }

    private boolean isFrontend(String repo) {
        switch (repo) {
            case "api":
            case "assets":
            case "favicon.png":
                return true;
            default: return false;
        }
    }
}
