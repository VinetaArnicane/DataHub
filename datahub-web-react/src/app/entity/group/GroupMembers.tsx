import { MoreOutlined, UserAddOutlined, UserDeleteOutlined } from '@ant-design/icons';
import { Col, Dropdown, Menu, message, Modal, Pagination, Row } from 'antd';
import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import styled from 'styled-components';
import { useGetGroupMembersLazyQuery, useRemoveGroupMembersMutation } from '../../../graphql/group.generated';
import { CorpUser, EntityRelationshipsResult, EntityType } from '../../../types.generated';
import { CustomAvatar } from '../../shared/avatar';
import { useEntityRegistry } from '../../useEntityRegistry';
import { AddGroupMembersModal } from './AddGroupMembersModal';

type Props = {
    urn: string;
    initialRelationships?: EntityRelationshipsResult | null;
    pageSize: number;
};

const ADD_MEMBER_STYLE = {
    backGround: '#ffffff',
    boxShadow: '0px 2px 6px rgba(0, 0, 0, 0.05)',
};
const AVATAR_STYLE = { margin: '5px 5px 5px 0' };

/**
 * Styled Components
 */
const AddMember = styled(Col)`
    font-family: Manrope;
    font-style: normal;
    font-weight: 500;
    font-size: 12px;
    line-height: 20px;
    color: #262626;
    padding: 13px 30px;
    cursor: pointer;

    &&&.anticon.anticon-user-add {
        margin-right: 6px;
    }
`;

const GroupMemberWrapper = styled.div`
    height: calc(100vh - 217px);
    overflow-y: auto;

    & .groupMemberRow {
        margin: 0 19px;
    }
`;

const MemberColumn = styled(Col)`
    padding: 19px 0 19px 0;
    border-bottom: 1px solid #f0f0f0;
`;

const MemberEditIcon = styled.div`
    font-size: 22px;
    float: right;
`;

const Name = styled.span`
    font-weight: bold;
    font-size: 14px;
    line-height: 22px;
    color: #262626;
`;

export default function GroupMembers({ urn, initialRelationships, pageSize }: Props) {
    const entityRegistry = useEntityRegistry();

    const [page, setPage] = useState(1);
    /* eslint-disable @typescript-eslint/no-unused-vars */
    const [isEditingMembers, setIsEditingMembers] = useState(false);
    const [getMembers, { data: membersData }] = useGetGroupMembersLazyQuery();
    const [removeGroupMembersMutation] = useRemoveGroupMembersMutation();

    const onChangeMembersPage = (newPage: number) => {
        setPage(newPage);
        const start = (newPage - 1) * pageSize;
        getMembers({ variables: { urn, start, count: pageSize } });
    };

    const removeGroupMember = (userUrn: string) => {
        removeGroupMembersMutation({
            variables: {
                groupUrn: urn,
                userUrns: [userUrn],
            },
        })
            .catch((e) => {
                message.destroy();
                message.error({ content: `Failed to remove group member!: \n ${e.message || ''}`, duration: 3 });
            })
            .finally(() => {
                message.success({
                    content: `Removed group member!`,
                    duration: 3,
                });
                // Hack to deal with eventual consistency
                setTimeout(function () {
                    // Reload the page.
                    onChangeMembersPage(page);
                }, 2000);
            });
    };

    const onClickEditMembers = () => {
        setIsEditingMembers(true);
    };

    const onAddMembers = () => {
        setTimeout(function () {
            // Reload the page.
            onChangeMembersPage(page);
        }, 3000);
    };

    const onRemoveMember = (memberUrn: string) => {
        Modal.confirm({
            title: `Confirm Group Member Removal`,
            content: `Are you sure you want to remove this user from the group?`,
            onOk() {
                removeGroupMember(memberUrn);
            },
            onCancel() {},
            okText: 'Yes',
            maskClosable: true,
            closable: true,
        });
    };

    const relationships = membersData ? membersData.corpGroup?.relationships : initialRelationships;
    const total = relationships?.total || 0;
    const groupMembers = relationships?.relationships?.map((rel) => rel.entity as CorpUser) || [];

    const onMemberMenuClick = (e, urnID) => {
        // TODO: add for make owner if required, else remove it
        if (e.key === 'remove') {
            onRemoveMember(urnID);
        }
    };

    const menu = (urnID) => {
        return (
            <Menu onClick={(e) => onMemberMenuClick(e, urnID)}>
                <Menu.Item disabled key="make">
                    <span>
                        <UserAddOutlined /> Make owner
                    </span>
                </Menu.Item>
                <Menu.Item key="remove">
                    <span>
                        <UserDeleteOutlined /> Remove from Group
                    </span>
                </Menu.Item>
            </Menu>
        );
    };

    return (
        <>
            <Row style={ADD_MEMBER_STYLE}>
                <AddMember onClick={onClickEditMembers}>
                    <UserAddOutlined />
                    Add Member
                </AddMember>
            </Row>
            <GroupMemberWrapper>
                <Row className="groupMemberRow">
                    {groupMembers &&
                        groupMembers.map((item) => {
                            return (
                                <>
                                    <MemberColumn xl={23} lg={23} md={23} sm={23} xs={23}>
                                        <Link to={entityRegistry.getEntityUrl(EntityType.CorpUser, item.urn)}>
                                            <CustomAvatar
                                                size={24}
                                                photoUrl={item.editableProperties?.pictureLink || ''}
                                                name={entityRegistry.getDisplayName(EntityType.CorpUser, item)}
                                                style={AVATAR_STYLE}
                                            />
                                            <Name>{entityRegistry.getDisplayName(EntityType.CorpUser, item)}</Name>
                                        </Link>
                                    </MemberColumn>
                                    <MemberColumn xl={1} lg={1} md={1} sm={1} xs={1}>
                                        <MemberEditIcon>
                                            <Dropdown overlay={menu(item.urn)}>
                                                <MoreOutlined />
                                            </Dropdown>
                                        </MemberEditIcon>
                                    </MemberColumn>
                                </>
                            );
                        })}
                </Row>
            </GroupMemberWrapper>
            <Row justify="center" style={{ marginTop: '15px' }}>
                <Pagination
                    current={page}
                    pageSize={pageSize}
                    total={total}
                    showLessItems
                    onChange={onChangeMembersPage}
                    showSizeChanger={false}
                />
            </Row>
            <AddGroupMembersModal
                urn={urn}
                visible={isEditingMembers}
                onSubmit={onAddMembers}
                onClose={() => setIsEditingMembers(false)}
            />
        </>
    );
}
