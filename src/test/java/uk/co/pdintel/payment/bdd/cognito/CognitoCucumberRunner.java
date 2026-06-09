package uk.co.pdintel.payment.bdd.cognito;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Separate runner for the Cognito BDD suite.
 *
 * Uses a dedicated glue package (bdd.cognito) so that CognitoHealthSteps can
 * declare its own @CucumberContextConfiguration without conflicting with the
 * main suite's HealthSteps. Cucumber requires exactly one
 * @CucumberContextConfiguration per glue path.
 *
 * Only @cognito-tagged scenarios are run, keeping this suite isolated from the
 * mock-auth suite that runs under bdd.CucumberRunner.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "uk.co.pdintel.payment.bdd.cognito,uk.co.pdintel.payment.bdd")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@cognito")
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty, json:target/cucumber-reports/cognito-cucumber.json, html:target/cucumber-reports/cognito-cucumber.html"
)
public class CognitoCucumberRunner {
}
