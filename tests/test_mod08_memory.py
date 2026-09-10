from shadow.memory.context import MemoryFacade

class Store:
    def __init__(self): self.data = {}
    def load(self, key): return self.data.get(key, {})
    def save(self, key, value): self.data[key] = value

def test_memory_facade_round_trip():
    store = Store()
    memory = MemoryFacade(store)
    ctx = memory.load("s1", user_id="u1", device_id="phone")
    ctx.facts["name"] = "user"
    memory.save(ctx)
    loaded = memory.load("s1", user_id="u1", device_id="phone")
    assert loaded.facts["name"] == "user"
    assert loaded.device_id == "phone"
