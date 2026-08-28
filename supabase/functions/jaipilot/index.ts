import { handleMcpHttpRequest } from "../../../mcp/jaipilot-mcp.ts";

Deno.serve((request) => handleMcpHttpRequest(request));
