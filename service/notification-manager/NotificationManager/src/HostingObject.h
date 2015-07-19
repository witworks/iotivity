//******************************************************************
//
// Copyright 2015 Samsung Electronics All Rights Reserved.
//
//-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
//-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

#ifndef RH_HOSTINGOBJECT_H_
#define RH_HOSTINGOBJECT_H_

#include "ResourceClient.h"
#include "ResourceObject.h"
#include "RequestObject.h"
#include "ResourceBroker.h"
#include "ResourceCacheManager.h"
#include "PrimitiveResource.h"

namespace OIC
{
namespace Service
{

class HostingObject
{
private:
    typedef std::shared_ptr<ResourceObject> ResourceObjectPtr;
    typedef std::shared_ptr<RemoteResourceObject> RemoteObjectPtr;
    typedef std::shared_ptr<RequestObject> RequestObjectPtr;
    typedef std::shared_ptr<PrimitiveResource> PrimiteveResourcePtr;

    typedef std::function<void(ResourceState)> BrokerCallback;
    typedef std::function<void(const ResourceAttributes &)> CacheCallback;
    typedef std::function<void()> DestroyedCallback;

    typedef std::function<
            RCSSetResponse(const RCSRequest&, ResourceAttributes&)> SetRequestHandler;

public:
    HostingObject();
    ~HostingObject() = default;

    void initializeHostingObject(RemoteObjectPtr rResource, DestroyedCallback destroyCB);

    RemoteObjectPtr getRemoteResource() const;

private:
    RemoteObjectPtr remoteObject;
    ResourceObjectPtr mirroredServer;

    ResourceState remoteState;

    BrokerCallback pStateChangedCB;
    CacheCallback pDataUpdateCB;
    DestroyedCallback pDestroyCB;

    SetRequestHandler pSetRequestHandler;

    ResourceObjectPtr createMirroredServer(RemoteObjectPtr rObject);

    void stateChangedCB(ResourceState state, RemoteObjectPtr rObject);
    void dataChangedCB(const ResourceAttributes & attributes, RemoteObjectPtr rObject);

    RCSSetResponse setRequestHandler(
            const RCSRequest & request, ResourceAttributes & attributes);

    void destroyHostingObject();

};

} /* namespace Service */
} /* namespace OIC */

#endif /* RH_HOSTINGOBJECT_H_ */
