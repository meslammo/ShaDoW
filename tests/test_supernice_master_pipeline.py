from shadow.supernice.master import SuperNiceMasterPipeline

def test_master_pipeline_completes_normal_request(tmp_path):
    result = SuperNiceMasterPipeline(str(tmp_path)).run("اكتبلي خطة لتنظيم مشروع SHADOW", authenticated=True, confirmed=True)
    assert result["ok"] is True
    assert result["status"] == "completed"
    assert result["contract"]["core_count"] == 150
    assert len(result["stages"]) == 12
    assert result["stages"][-1]["stage"] == "deliver"

def test_master_pipeline_blocks_risky_request_without_confirmation(tmp_path):
    result = SuperNiceMasterPipeline(str(tmp_path)).run("احذف الملف ده", authenticated=True, confirmed=False)
    assert result["ok"] is False
    assert result["status"] == "confirmation_required"
    assert result["startup_blocking"] is False

def test_master_pipeline_marks_web_when_fresh(tmp_path):
    result = SuperNiceMasterPipeline(str(tmp_path)).run("ابحثلي عن آخر تحديثات SHADOW", authenticated=True, confirmed=True)
    web = next(x for x in result["stages"] if x["stage"] == "web_discovery")
    assert web["status"] in {"executed","adapter_pending","adapter_ready","handler_error"}

def test_master_pipeline_has_all_twelve_stage_names():
    assert len(SuperNiceMasterPipeline.STAGE_NAMES) == 12
    assert SuperNiceMasterPipeline.STAGE_NAMES[0] == "understand"
    assert SuperNiceMasterPipeline.STAGE_NAMES[-1] == "deliver"
