using Pkg
Pkg.instantiate()

using Sockets
using DebugAdapter
using Logging

function start_debugger()
    try
        server_port = parse(Int, ARGS[1])
        server = Sockets.listen(server_port)
        println("Listening on port $server_port")

        conn = Sockets.accept(server)
        println("Client connected")

        debugsession = DebugAdapter.DebugSession(conn)
        run(debugsession)

        close(conn)
    catch e
        println("Error: ", e)
    end
end
start_debugger()