import * as vscode from 'vscode';

export interface ExportedFunction {
    name: string;
    parameters: Parameter[];
    returnType: string;
    description: string;
    objectNames?: string[];  // Changed from objectName to objectNames array
}

export interface Parameter {
    name: string;
    type: string;
    defaultValue?: string;  // Added to support default parameter values
}

export class KotlinParser {
    private static readonly EXPORT_FLAG_REGEX = /@ExportFlag\s*(?:\(([^)]*)\))?/;
    private static readonly HOST_ACCESS_OBJECT_REGEX = /@HostAccess\.Object\s*\(\s*name\s*=\s*"([^"]+)"\s*\)/;
    private static readonly HOST_ACCESS_EXPORT_REGEX = /@HostAccess\.Export\s*(?:\(([^)]*)\))?/;
    private static readonly FUNCTION_REGEX = /fun\s+(\w+)\s*\(([^)]*)\)\s*(?::\s*([^{]+))?/;

    public static parseKotlinFile(content: string): ExportedFunction[] {
        const lines = content.split('\n');
        const functions: ExportedFunction[] = [];
        let currentFunction: Partial<ExportedFunction> | null = null;
        let currentComment = '';
        let currentObjectNames: string[] | undefined;

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i].trim();

            // Skip empty lines
            if (!line) continue;

            // Check for comments
            if (line.startsWith('//')) {
                currentComment += line.substring(2).trim() + ' ';
                continue;
            }

            // Check for @HostAccess.Object annotation
            const objectMatch = line.match(this.HOST_ACCESS_OBJECT_REGEX);
            if (objectMatch) {
                // Split the object names by comma and trim each name
                currentObjectNames = objectMatch[1].split(',').map(name => name.trim());
                continue;
            }

            // Check for @ExportFlag or @HostAccess.Export annotation
            const exportMatch = line.match(this.EXPORT_FLAG_REGEX) || line.match(this.HOST_ACCESS_EXPORT_REGEX);
            if (exportMatch) {
                // Next line should be the function definition
                if (i + 1 < lines.length) {
                    const functionLine = lines[i + 1].trim();
                    const functionMatch = functionLine.match(this.FUNCTION_REGEX);
                    
                    if (functionMatch) {
                        const [_, name, params, returnType] = functionMatch;
                        currentFunction = {
                            name,
                            parameters: this.parseParameters(params),
                            returnType: returnType ? returnType.trim() : 'Unit',
                            description: currentComment.trim(),
                            objectNames: currentObjectNames
                        };
                        functions.push(currentFunction as ExportedFunction);
                        currentFunction = null;
                        currentComment = '';
                    }
                }
            }
        }

        return functions;
    }

    private static parseParameters(paramsString: string): Parameter[] {
        if (!paramsString.trim()) return [];

        return paramsString.split(',').map(param => {
            const trimmedParam = param.trim();
            const [nameAndType, defaultValue] = trimmedParam.split('=').map(s => s.trim());
            const [name, type] = nameAndType.split(':').map(s => s.trim());
            return { 
                name, 
                type,
                defaultValue: defaultValue
            };
        });
    }

    public static createCompletionItems(functions: ExportedFunction[]): vscode.CompletionItem[] {
        return functions.map(func => {
            const item = new vscode.CompletionItem(func.name, vscode.CompletionItemKind.Function);
            
            // Create snippet string for parameters
            const paramSnippets = func.parameters.map((param, index) => {
                const defaultValue = param.defaultValue ? ` = ${param.defaultValue}` : '';
                return `\${${index + 1}:${param.name}: ${param.type}${defaultValue}}`;
            });
            const snippetString = `${func.name}(${paramSnippets.join(', ')})`;
            
            item.insertText = new vscode.SnippetString(snippetString);
            item.documentation = new vscode.MarkdownString(func.description);
            
            // Format the detail string to show parameter types and return type
            const paramDetails = func.parameters.map(p => {
                const defaultValue = p.defaultValue ? ` = ${p.defaultValue}` : '';
                return `${p.name}: ${p.type}${defaultValue}`;
            });
            item.detail = `(${paramDetails.join(', ')}) -> ${func.returnType}`;
            
            return item;
        });
    }

    // Modified method to get functions by object name
    public static getFunctionsByObject(functions: ExportedFunction[], objectName: string): ExportedFunction[] {
        return functions.filter(func => func.objectNames?.includes(objectName));
    }
} 