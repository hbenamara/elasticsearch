/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.update;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.AbstractExecutableScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.NativeScriptEngineService;
import org.elasticsearch.script.NativeScriptFactory;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.elasticsearch.test.ESIntegTestCase.Scope;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

@ClusterScope(scope= Scope.SUITE, numDataNodes =1)
public class UpdateByNativeScriptIT extends ESIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(CustomNativeScriptFactory.TestPlugin.class);
    }

    public void testThatUpdateUsingNativeScriptWorks() throws Exception {
        createIndex("test");

        index("test", "type", "1", "text", "value");

        Map<String, Object> params = new HashMap<>();
        params.put("foo", "SETVALUE");
        client().prepareUpdate("test", "type", "1")
                .setScript(new Script(ScriptType.INLINE, NativeScriptEngineService.NAME, "custom", params)).get();

        Map<String, Object> data = client().prepareGet("test", "type", "1").get().getSource();
        assertThat(data, hasKey("foo"));
        assertThat(data.get("foo").toString(), is("SETVALUE"));
    }

    public static class CustomNativeScriptFactory implements NativeScriptFactory  {
        public static class TestPlugin extends Plugin implements ScriptPlugin {
            @Override
            public List<NativeScriptFactory> getNativeScripts() {
                return Collections.singletonList(new CustomNativeScriptFactory());
            }
        }
        @Override
        public ExecutableScript newScript(@Nullable Map<String, Object> params) {
            return new CustomScript(params);
        }
        @Override
        public boolean needsScores() {
            return false;
        }

        @Override
        public String getName() {
            return "custom";
        }
    }

    static class CustomScript extends AbstractExecutableScript {
        private Map<String, Object> params;
        private Map<String, Object> vars = new HashMap<>(2);

        public CustomScript(Map<String, Object> params) {
            this.params = params;
        }

        @Override
        public Object run() {
            if (vars.containsKey("ctx") && vars.get("ctx") instanceof Map) {
                Map ctx = (Map) vars.get("ctx");
                if (ctx.containsKey("_source") && ctx.get("_source") instanceof Map) {
                    Map source = (Map) ctx.get("_source");
                    source.putAll(params);
                }
            }
            // return value does not matter, the UpdateHelper class
            return null;
        }

        @Override
        public void setNextVar(String name, Object value) {
            vars.put(name, value);
        }

    }

}
