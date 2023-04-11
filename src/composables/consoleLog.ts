export function useLog() {
    const debug = (data: any) => {
        out('debug', data)
    }

    const info = (data: any) => {
        out('info', data)
    }

    const warn = (data: any) => {
        out('warn', data)
    }

    const error = (data: any) => {
        out('error', data)
    }

    const out = (level: string, data: any) => {
        try {
            vscode.postMessage({
                command: 'consoleLog',
                level: level,
                data: data
            })
        }
        catch (error) {
            vscode.postMessage({
                command: 'consoleLog',
                level: level,
                data: JSON.stringify(data)
            })
        }
    }

    const isJson = (data: any) => {
        try {
            JSON.parse(data);
        } catch (error) {
            return false;
        }
        return true;
    }

    return {
        debug,
        info,
        warn,
        error
    }
}