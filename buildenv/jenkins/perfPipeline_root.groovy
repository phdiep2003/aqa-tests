#!groovy

def PLATFORMS = params.PLATFORMS.trim().split("\\s*,\\s*")
def BENCHMARKS = params.BENCHMARKS.trim().split("\\s*,\\s*")

def JOBS =[:]
PLATFORMS.each { PLATFORM ->
    BENCHMARKS.each { BENCHMARK ->
        def childParams = []
        // loop through all the params and change the parameters if needed
        params.each { param ->
            // Exclude unnecessary parameters for downstream jobs
            if (param == "PLATFORMS" || param == "BENCHMARKS") {
                // do nothing
            }
            def value = param.value.toString()
            if (value == "true" || value == "false") {
                childParams << booleanParam(name: param.key, value: value.toBoolean())
            } else {
                childParams << string(name: param.key, value: value)
            }
        }
        childParams << string(name: "PLATFORM", value: PLATFORM)
        childParams << string(name: "BENCHMARK", value: BENCHMARK)
        def shortName = "j9"
        if (params.JDK_IMPL) {
            if (params.JDK_IMPL == "hotspot") {
                shortName = "hs"
            }
        }

}
node("worker || (ci.role.test&&hw.arch.x86&&sw.os.linux)") {
        perfConfigJson.each { item ->
                def BENCHMARK = item.BENCHMARK 
                def TARGET = item.TARGET
                def BUILD_LIST = item.BUILD_LIST
                def PLATMACHINE_MAP = item.PLAT_MACHINE_MAP
                def baseParams = childParams.collect()
                baseParams << string(name: "BENCHMARK", value: item.BENCHMARK)
                baseParams << string(name: "TARGET", value: item.TARGET)
                baseParams << string(name: "BUILD_LIST", value: item.BUILD_LIST)
                baseParams << string(name: "PERF_ITERATIONS", value: item.PERF_ITERATIONS ? item.PERF_ITERATIONS.toString() : "4")
                
                item.PLAT_MACHINE_MAP.each { kv -> 
                        kv.each {p, m -> 
                                // Clone baseParams to avoid mutation
                                def thisChildParams = baseParams.collect()
                                thisChildParams << string(name: "PLATFORM", value: p)
                                thisChildParams << string(name: "LABEL", value: m)

                                def shortName = (params.JDK_IMPL && params.JDK_IMPL == "hotspot") ? "hs" : "j9"
                                def jobName = "Perf_openjdk${params.JDK_VERSION}_${shortName}_sanity.perf_${p}_${item.BENCHMARK}"
                                def jobIsRunnable = JobHelper.jobIsRunnable(jobName)
                                echo "jobName ${jobName} params: ${thisChildParams}"
                                if (!jobIsRunnable) {
                                        echo "Generating downstream job '${jobName}' from perfL2JobTemplate …"
                                        createPerfL2Job(jobName, p, item.BENCHMARK)
                                }
                                JOBS[jobName] = {
                                        build job: jobName, parameters: thisChildParams, propagate: true
                                }
                        }
                }
        }
    }
}

parallel JOBS
