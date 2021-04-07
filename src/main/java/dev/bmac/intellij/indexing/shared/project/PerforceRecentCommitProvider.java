package dev.bmac.intellij.indexing.shared.project;

import com.intellij.indexing.shared.ultimate.project.ProjectSharedIndexRecentCommits;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.perforce.perforce.CommandArguments;
import org.jetbrains.idea.perforce.perforce.ExecResult;
import org.jetbrains.idea.perforce.perforce.P4File;
import org.jetbrains.idea.perforce.perforce.PerforceRunner;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManager;
import org.jetbrains.idea.perforce.perforce.connections.PerforceConnectionManagerI;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PerforceRecentCommitProvider implements ProjectSharedIndexRecentCommits {
    private static final Pattern CSTAT_PATTERN = Pattern.compile("\\.\\.\\. change (\\d+)");
    @NotNull
    @Override
    public List<String> listRecentCommits(@NotNull Project project, @NotNull ProgressIndicator progressIndicator) {
        P4File projectRoot = P4File.create(project.getProjectFile().getParent().getParent());
        PerforceRunner runner = PerforceRunner.getInstance(project);
        CommandArguments commandArguments = new CommandArguments();
        //cstat returns a list of changes the client is aware of, which if it has (#have) them, can be used to determine
        //the latest the client has
        commandArguments.append("cstat").append(projectRoot.getRecursivePath() + "#have");
        PerforceConnectionManagerI manager = PerforceConnectionManager.getInstance(project);
        ExecResult execResult = runner.executeP4Command(commandArguments.getArguments(), manager.getConnectionForFile(projectRoot));
        if (execResult.getExitCode() != 0) {
            //TODO error handling!!!
            return Collections.emptyList();
        }
        return getRecentHaveChanges(execResult.getStdout());

    }

    private static List<String> getRecentHaveChanges(String output) {
        LinkedList<String> result = new LinkedList<>();
        LineNumberReader reader = new LineNumberReader(new StringReader(output));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = CSTAT_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String changelist = matcher.group(1);
                    result.add(changelist);
                    //TODO add registry value
                    //cstat can return a lot of records, pop items from the start (earliest)
                    if (result.size() > 5000) {
                        result.remove();
                    }
                }
            }
        } catch (IOException e) {
            //TODO error handling
            e.printStackTrace();
        }
        Collections.reverse(result);
        return result;
    }
}
