# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# NO CHECKED-IN PROTOBUF GENCODE
# source: Capacity.proto
# Protobuf Python Version: 5.29.3
"""Generated protocol buffer code."""
from google.protobuf import descriptor as _descriptor
from google.protobuf import descriptor_pool as _descriptor_pool
from google.protobuf import runtime_version as _runtime_version
from google.protobuf import symbol_database as _symbol_database
from google.protobuf.internal import builder as _builder
_runtime_version.ValidateProtobufRuntimeVersion(
    _runtime_version.Domain.PUBLIC,
    5,
    29,
    3,
    '',
    'Capacity.proto'
)
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()




DESCRIPTOR = _descriptor_pool.Default().AddSerializedFile(b'\n\x0e\x43\x61pacity.proto\x12\x0f\x63om.hasup.proto\"G\n\x08\x43\x61pacity\x12\x11\n\tserver_id\x18\x01 \x01(\x05\x12\x15\n\rserver_status\x18\x02 \x01(\x05\x12\x11\n\ttimestamp\x18\x03 \x01(\x03\"$\n\x0f\x43\x61pacityRequest\x12\x11\n\tserver_id\x18\x01 \x01(\x05\"A\n\x10\x43\x61pacityResponse\x12-\n\ncapacities\x18\x01 \x03(\x0b\x32\x19.com.hasup.proto.CapacityB(\n\x0f\x63om.hasup.protoB\rCapacityProto\xea\x02\x05Hasupb\x06proto3')

_globals = globals()
_builder.BuildMessageAndEnumDescriptors(DESCRIPTOR, _globals)
_builder.BuildTopDescriptorsAndMessages(DESCRIPTOR, 'Capacity_pb2', _globals)
if not _descriptor._USE_C_DESCRIPTORS:
  _globals['DESCRIPTOR']._loaded_options = None
  _globals['DESCRIPTOR']._serialized_options = b'\n\017com.hasup.protoB\rCapacityProto\352\002\005Hasup'
  _globals['_CAPACITY']._serialized_start=35
  _globals['_CAPACITY']._serialized_end=106
  _globals['_CAPACITYREQUEST']._serialized_start=108
  _globals['_CAPACITYREQUEST']._serialized_end=144
  _globals['_CAPACITYRESPONSE']._serialized_start=146
  _globals['_CAPACITYRESPONSE']._serialized_end=211
# @@protoc_insertion_point(module_scope)
