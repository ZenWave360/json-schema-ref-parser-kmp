import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import {
    parseSchemaText,
    dereferenceSchema,
    dereferenceSchemaText,
} from '@zenwave360/json-schema-ref-parser-kmp';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');
const resourcesDir = path.join(repoRoot, 'src', 'commonTest', 'resources');

describe('JSON Schema Ref Parser KMP Node smoke tests', () => {
    it('parses inline schema text and records source locations', () => {
        const document = parseSchemaText(`
type: object
properties:
  id:
    type: string
required:
  - id
`.trim(), 'memory://node-smoke.yaml');

        assert.equal(document.schema.type, 'object');
        assert.equal(document.schema.properties.id.type, 'string');
        assert.deepEqual(document.schema.required, ['id']);
        assert.equal(document.locations['/properties/id/type'].file, 'memory://node-smoke.yaml');
        assert.equal(document.locations['/properties/id/type'].line, 3);
    });

    it('dereferences inline schema text and tracks resolved refs', async () => {
        const document = await dereferenceSchemaText(`
definitions:
  Address:
    type: object
    properties:
      street:
        type: string
type: object
properties:
  shippingAddress:
    $ref: '#/definitions/Address'
`.trim(), 'memory://inline-ref.yaml');

        assert.equal(document.schema.properties.shippingAddress.type, 'object');
        assert.equal(document.schema.properties.shippingAddress.properties.street.type, 'string');
        assert.equal(document.resolvedRefs.length, 1);
        assert.equal(document.resolvedRefs[0].refString, '#/definitions/Address');
    });

    it('dereferences file-based refs, merges allOf, and reports file locations', async () => {
        const rootSchemaPath = path.join(resourcesDir, 'GH-36', 'root.json');
        const rootSchemaUri = pathToFileURL(rootSchemaPath).href;

        const document = await dereferenceSchema(rootSchemaUri, true);

        assert.equal(document.schema.type, 'object');
        assert.equal(document.schema.properties.a.type, 'integer');
        assert.equal(document.schema.properties.ingressDomain.type, 'string');
        assert.deepEqual([...document.schema.required].sort(), ['a', 'ingressDomain']);

        const ingressLocation = document.documentLocations[rootSchemaUri.replace('root.json', 'common.schema.json')]['/properties/ingressDomain/type'];
        assert.equal(ingressLocation.file, rootSchemaUri.replace('root.json', 'common.schema.json'));
    });

    it('keeps external file information in resolved refs and source locations', async () => {
        const asyncApiPath = path.join(resourcesDir, 'asyncapi', 'original-ref', 'asyncapi-original-ref.yml');
        const asyncApiUri = pathToFileURL(asyncApiPath).href;
        const externalSchemaUri = pathToFileURL(path.join(resourcesDir, 'asyncapi', 'original-ref', 'schema.yaml')).href;

        const document = await dereferenceSchema(asyncApiUri);

        assert.equal(document.schema.components.schemas.UserSignedUpPayload.allOf[0].type, 'object');
        assert.equal(document.resolvedRefs.some(ref => ref.targetUri === externalSchemaUri), true);
        assert.equal(
            document.documentLocations[externalSchemaUri]['/BusinessEventMetadata/properties/data/type'].file,
            externalSchemaUri,
        );
    });
});
