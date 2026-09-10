from shadow.memory.persistent import PersistentMemory
from shadow.devices.registry import Device, DeviceRegistry
from shadow.security.permissions import PermissionManager
from shadow.autonomy.engine import AutonomyEngine, AutomationRule
from shadow.self_development.pipeline import SelfDevelopmentPipeline, ChangeProposal

def test_memory_roundtrip(tmp_path):
    m=PersistentMemory(tmp_path/'m.jsonl'); m.put('محمد prefers concise answers',kind='preference',tags=['preference'])
    assert m.search('prefers concise')[0].kind=='preference'
    assert 'concise' in m.export()

def test_device_authorization():
    r=DeviceRegistry(); r.register(Device('phone','My Phone','android')); r.authorize('phone')
    d=r.heartbeat('phone',battery=72,location='Cairo')
    assert d.authorized and d.online and d.battery==72 and d.last_location=='Cairo'

def test_permissions_fail_closed():
    p=PermissionManager(); assert not p.decide('home.control').allowed
    assert p.decide('home.control').requires_confirmation
    assert p.decide('home.control',confirmed=True).allowed

def test_autonomy_and_self_development():
    a=AutonomyEngine(); a.add_rule(AutomationRule('r1','wake','say hello'))
    reports=a.trigger('wake',lambda goal: goal=='say hello'); assert reports[0].success and reports[0].verified
    s=SelfDevelopmentPipeline(); s.propose(ChangeProposal('p1','x','y'))
    p=s.validate('p1',lambda:True,lambda:True,lambda:True); assert p.verified
    assert s.approve('p1').approved
    assert not s.rollback('p1').approved
