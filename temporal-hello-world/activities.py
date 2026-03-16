from temporalio import activity

@activity.defn
async def say_hello(name: str) -> str:
    print(f"[Activity] Running say_hello for: {name}")
    return f"Hello, {name}!"
