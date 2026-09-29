from shadow.supernice.integration import SuperNiceRuntime

def test_supernice_bridges_original_12_core_root():
    runtime=SuperNiceRuntime()
    assert runtime.status()["core_count"]==150
    result=runtime.execute("CORE-003","build plan")
    assert result.ok is True
    assert result.metadata["source"]=="TwelveCoreRuntime"
