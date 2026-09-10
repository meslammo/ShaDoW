from shadow.devices.domains import DomainRegistry
from shadow.runtime.runtime import ShadowRuntime
from shadow.security.permissions import PermissionManager


def test_home_and_car_domains_are_real_runtime_boundaries():
    domains = DomainRegistry()
    home = domains.status("home")
    car = domains.status("car")
    assert home["adapter_ready"] and not home["connected"] and not home["endpoint_configured"]
    assert car["adapter_ready"] and not car["connected"] and not car["endpoint_configured"]


def test_domain_tools_are_registered_and_safe_reads_are_allowed():
    runtime = ShadowRuntime()
    assert {"home_status", "car_status", "home_control", "car_control"}.issubset(set(runtime.tools.names()))
    assert PermissionManager().decide("home_status").allowed
    assert PermissionManager().decide("car_status").allowed
    assert PermissionManager().decide("home_control").requires_confirmation
    assert PermissionManager().decide("car_control").requires_confirmation


def test_domain_control_fails_closed_even_after_confirmation_without_endpoint():
    runtime = ShadowRuntime()
    result = runtime.executor.execute("home_control", confirmed=True, capability="home.control", payload={"action": "lights.on"})
    assert not result.success
    assert "endpoint not configured" in result.output
    result = runtime.executor.execute("car_control", confirmed=True, capability="car.control", payload={"action": "status"})
    assert not result.success
    assert "endpoint not configured" in result.output
